package com.github.swagger.scala.converter

import scala.collection.concurrent.TrieMap
import scala.quoted.*

/** Registry of Scala 3 type information that is not available at runtime.
  *
  * The JVM erases the element types of generic Scala types - `Option[Long]` is compiled to `Option<Object>` - and Scala 3 does not record
  * `sealed` hierarchies in the class file. That information only exists in the TASTy files and in the compiler, so it has to be captured at
  * compile time. [[ScalaModelRegistry.register]] is a macro that reads it from the type you give it and stores it here for the converter to
  * use at runtime.
  *
  * Register the model classes used in your API before any schema is generated, for example:
  * {{{
  * ScalaModelRegistry.register[PetOwner]
  * ScalaModelRegistry.registerAll[(Order, Invoice)]
  * }}}
  *
  * Registration is recursive: the field types, collection element types, base classes and sealed subtypes of the type you register are
  * registered too, so in practice only the top level model classes need to be listed.
  *
  * Unregistered classes still get schemas, but `Option[Int]` and `Seq[Int]` fields are typed as objects rather than integers and sealed
  * hierarchies do not get an `anyOf` schema. A warning is logged the first time such a class is seen.
  *
  * This object also exists in the Scala 2 build, where all methods are no-ops, so that cross built code compiles unchanged. Scala 2 gets
  * the same information from `scala-reflect` at runtime.
  *
  * @since v2.16.0
  */
object ScalaModelRegistry {
  private val erasedPrimitivesByClass = TrieMap[Class[?], Map[String, Class[?]]]()
  private val subtypesByClass = TrieMap[Class[?], Seq[Class[?]]]()

  /** Captures the type information of `T` (and of everything reachable from it) so that the converter can generate accurate schemas for it.
    */
  inline def register[T]: Unit = ${ ScalaModelRegistryMacros.registerImpl[T] }

  /** Same as [[register]] for each member of a tuple of types, e.g. `registerAll[(Cat, Dog)]`. */
  inline def registerAll[T <: Tuple]: Unit = ${ ScalaModelRegistryMacros.registerAllImpl[T] }

  /** @return true if the type information for this class has been registered */
  def isRegistered(cls: Class[?]): Boolean = erasedPrimitivesByClass.contains(cls)

  /** @return the classes that have been registered so far */
  def registeredClasses: Set[Class[?]] = erasedPrimitivesByClass.keySet.toSet

  /** Removes all registered type information. */
  def clear(): Unit = {
    erasedPrimitivesByClass.clear()
    subtypesByClass.clear()
  }

  /** Called by the code that [[register]] generates - not intended to be called directly. */
  def registerType(cls: Class[?], erasedPrimitives: Map[String, Class[?]], subtypes: Seq[Class[?]]): Unit = {
    erasedPrimitivesByClass.put(cls, erasedPrimitives)
    subtypesByClass.put(cls, subtypes)
  }

  private[converter] def erasedPrimitives(cls: Class[?]): Option[Map[String, Class[?]]] = erasedPrimitivesByClass.get(cls)

  private[converter] def subtypes(cls: Class[?]): Option[Seq[Class[?]]] = subtypesByClass.get(cls)
}

private[converter] object ScalaModelRegistryMacros {
  private val ExcludedPackages = Seq("scala.", "java.", "javax.")

  def registerImpl[T: Type](using Quotes): Expr[Unit] = {
    import quotes.reflect.*
    registerTypes(List(TypeRepr.of[T]))
  }

  def registerAllImpl[T <: Tuple: Type](using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    // a tuple type is either a TupleN, whose type arguments are the types to register, or a `head *: tail` chain ended by EmptyTuple
    def tupleTypes(tpe: TypeRepr): List[TypeRepr] = tpe.dealias match {
      case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.name == "*:" => head :: tupleTypes(tail)
      case other => other.typeArgs
    }

    registerTypes(tupleTypes(TypeRepr.of[T]))
  }

  private def registerTypes(using Quotes)(types: List[quotes.reflect.TypeRepr]): Expr[Unit] = {
    import quotes.reflect.*

    val statements = List.newBuilder[Expr[Unit]]
    val visited = collection.mutable.Set.empty[String]

    def visit(tpe: TypeRepr): Unit = {
      val dealiased = tpe.dealias
      val sym = dealiased.typeSymbol
      if (isModelType(dealiased) && visited.add(sym.fullName)) {
        statements += registerStatement(dealiased)
        relatedTypes(dealiased).foreach(visit)
      }
    }

    types.foreach(visit)
    Expr.block(statements.result(), '{ () })
  }

  /** The runtime registration call for a single class. */
  private def registerStatement(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[Unit] = {
    import quotes.reflect.*

    // the class and the entries have to be turned into expressions here, outside of the quote, because the TypeRepr of this Quotes
    // instance is not in scope inside it
    val entries = Expr.ofList(fieldsOf(tpe).flatMap { case (name, fieldType) =>
      erasedPrimitiveOf(fieldType).map { primitive =>
        val nameExpr = Expr(name)
        val primitiveExpr = classExpr(primitive)
        '{ Tuple2($nameExpr, $primitiveExpr) }
      }
    })
    val subtypes = Expr.ofList(
      tpe.typeSymbol.children
        .map(child => if (child.flags.is(Flags.Module)) child.moduleClass.typeRef else child.typeRef)
        .sortBy(_.typeSymbol.fullName)
        .map(classExpr)
    )
    val registeredClass = classExpr(tpe)

    '{ ScalaModelRegistry.registerType($registeredClass, Map($entries*), $subtypes) }
  }

  /** `classOf[tpe]` - `Literal(ClassOfConstant(...))` is used because `classOf` needs a class type known to the compiler. */
  private def classExpr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[Class[?]] = {
    import quotes.reflect.*
    Literal(ClassOfConstant(tpe)).asExprOf[Class[?]]
  }

  /** The fields of a class by the name that Jackson uses for them: the constructor parameters and the (possibly inherited) vals. */
  private def fieldsOf(using Quotes)(tpe: quotes.reflect.TypeRepr): List[(String, quotes.reflect.TypeRepr)] = {
    import quotes.reflect.*
    val sym = tpe.typeSymbol
    val constructor = sym.primaryConstructor
    val constructorParams =
      if (constructor.isNoSymbol) Nil
      else constructor.paramSymss.flatten.filterNot(_.isTypeParam).map(param => param.name.trim -> param.termRef.widen)
    val fields = sym.fieldMembers.filterNot(_.flags.is(Flags.Synthetic)).map(field => field.name.trim -> tpe.memberType(field))
    (constructorParams ++ fields).distinctBy(_._1)
  }

  /** The primitive class that a field of this type has been erased to, if any. Mirrors the Scala 2 behaviour: only the element type of
    * `Option`, `Map` (the value type) and other collections is looked at, and nested wrappers such as `Option[Seq[Long]]` are unwrapped
    * until a non collection type is reached.
    */
  private def erasedPrimitiveOf(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    @annotation.tailrec
    def innermost(current: TypeRepr): TypeRepr = elementTypeOf(current) match {
      case Some(element) => innermost(element)
      case None => current
    }

    elementTypeOf(tpe).map(innermost).filter(isPrimitive)
  }

  /** The element type of an `Option`, collection or `Array`, or the value type of a `Map`. */
  private def elementTypeOf(using Quotes)(tpe: quotes.reflect.TypeRepr): Option[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*
    val dealiased = tpe.dealias
    val OptionClass = Symbol.classSymbol("scala.Option")
    val MapClass = Symbol.classSymbol("scala.collection.Map")
    val IterableClass = Symbol.classSymbol("scala.collection.Iterable")
    if (dealiased.typeSymbol == defn.ArrayClass) dealiased.typeArgs.headOption
    else if (dealiased.derivesFrom(OptionClass)) dealiased.baseType(OptionClass).typeArgs.headOption
    // IntMap[Long] and LongMap[Long] only have one type argument, so the value type is always the last one
    else if (dealiased.derivesFrom(MapClass)) dealiased.baseType(MapClass).typeArgs.lastOption
    else if (dealiased.derivesFrom(IterableClass)) dealiased.baseType(IterableClass).typeArgs.headOption
    else None
  }

  private def isPrimitive(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    val primitives =
      Seq(
        TypeRepr.of[Boolean],
        TypeRepr.of[Byte],
        TypeRepr.of[Char],
        TypeRepr.of[Short],
        TypeRepr.of[Int],
        TypeRepr.of[Long],
        TypeRepr.of[Float],
        TypeRepr.of[Double]
      )
    primitives.exists(_ =:= tpe.dealias)
  }

  /** The types reachable from this one that are worth registering as well. */
  private def relatedTypes(using Quotes)(tpe: quotes.reflect.TypeRepr): List[quotes.reflect.TypeRepr] = {
    import quotes.reflect.*

    def typeAndArgs(current: TypeRepr): List[TypeRepr] = {
      val dealiased = current.dealias
      dealiased :: dealiased.typeArgs.flatMap(typeAndArgs)
    }

    val fieldTypes = fieldsOf(tpe).flatMap { case (_, fieldType) => typeAndArgs(fieldType) }
    val baseTypes = tpe.baseClasses.filterNot(_ == tpe.typeSymbol).map(_.typeRef)
    val subtypes = tpe.typeSymbol.children.filterNot(_.flags.is(Flags.Module)).map(_.typeRef)
    fieldTypes ++ baseTypes ++ subtypes
  }

  private def isModelType(using Quotes)(tpe: quotes.reflect.TypeRepr): Boolean = {
    import quotes.reflect.*
    val sym = tpe.typeSymbol
    !sym.isNoSymbol && sym.isClassDef && !sym.flags.is(Flags.JavaDefined) && !ExcludedPackages.exists(sym.fullName.startsWith)
  }
}

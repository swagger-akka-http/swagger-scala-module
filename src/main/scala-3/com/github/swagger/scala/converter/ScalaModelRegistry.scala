package com.github.swagger.scala.converter

import scala.collection.concurrent.TrieMap
import scala.quoted.*

/** Registry of the Scala 3 type information that is not available at runtime.
  *
  * The JVM erases the element types of generic Scala types - `Option[Long]` is compiled to `Option<Object>` - and Scala 3 does not record
  * `sealed` hierarchies in the class file. That information only exists in the TASTy files and in the compiler, so it has to be captured at
  * compile time. There are two ways of doing that, and they can be mixed:
  *
  *   - derive [[ScalaTypeInfo]] on the model class itself, which needs nothing else to be called:
  *     {{{
  *     case class Pet(name: String, age: Option[Int]) derives ScalaTypeInfo
  *     }}}
  *   - register the class here, which is what classes that cannot be given a `derives` clause need:
  *     {{{
  *     ScalaModelRegistry.register[PetOwner]
  *     ScalaModelRegistry.registerAll[(Order, Invoice)]
  *     }}}
  *
  * Both are recursive: the field types, collection element types, base classes and sealed subtypes of the type are registered too, so in
  * practice only the top level model classes need to be covered. Registration has to happen before the schemas are generated.
  *
  * Classes that are neither derived nor registered still get schemas, but `Option[Int]` and `Seq[Int]` fields are typed as objects rather
  * than integers and sealed hierarchies do not get an `anyOf` schema. A warning is logged the first time such a class is seen.
  *
  * This object also exists in the Scala 2 build, where all methods are no-ops, so that cross built code compiles unchanged. Scala 2 gets
  * the same information from `scala-reflect` at runtime.
  *
  * @since v2.16.0
  */
object ScalaModelRegistry {

  /** Holds the type information of the classes that have been registered, and the outcome of looking up the classes that have not - the
    * lookup is by Java reflection, so it is worth not repeating it.
    */
  private val infoByClass = TrieMap[Class[?], Option[ScalaTypeInfo[?]]]()

  /** Captures the type information of `T` (and of everything reachable from it) so that the converter can generate accurate schemas for it.
    *
    * A generic class has one runtime class for all of its type arguments, so the element types that depend on one of its type parameters
    * are not recorded - `register[ListReply[String]]` and `register[ListReply[Long]]` record the same thing, and registering both, in any
    * order, is safe.
    */
  inline def register[T]: Unit = ${ ScalaModelRegistryMacros.registerImpl[T] }

  /** Same as [[register]] for each member of a tuple of types, e.g. `registerAll[(Cat, Dog)]`. */
  inline def registerAll[T <: Tuple]: Unit = ${ ScalaModelRegistryMacros.registerAllImpl[T] }

  /** @return true if the type information of this class is available, whether it was registered or derived */
  def isRegistered(cls: Class[?]): Boolean = infoFor(cls).isDefined

  /** @return the classes whose type information is available so far */
  def registeredClasses: Set[Class[?]] = infoByClass.iterator.collect { case (cls, Some(_)) => cls }.toSet

  /** Removes all registered type information. Type information that was derived on the class itself is found again when it is next needed.
    */
  def clear(): Unit = infoByClass.clear()

  /** Called by the code that [[register]] and [[ScalaTypeInfo.derived]] generate - not intended to be called directly. */
  def registerType(info: ScalaTypeInfo[?]): Unit = {
    infoByClass.put(info.modelClass, Some(info))
    info.related.foreach(registerType)
  }

  /** The type information of this class, either registered or derived on the class itself. */
  private[converter] def infoFor(cls: Class[?]): Option[ScalaTypeInfo[?]] = {
    infoByClass.getOrElseUpdate(
      cls, {
        val derived = ScalaTypeInfo.findDerived(cls)
        // a derived instance carries the types reachable from the class, which are worth keeping now that it has been read
        derived.foreach(info => info.related.foreach(registerType))
        derived
      }
    )
  }
}

private[converter] object ScalaModelRegistryMacros {
  private val ExcludedPackages = Seq("scala.", "java.", "javax.")

  def registerImpl[T: Type](using Quotes): Expr[Unit] = {
    import quotes.reflect.*
    Expr.block(registrations(List(TypeRepr.of[T])), '{ () })
  }

  /** The type information of `T`, carrying that of the types reachable from `T`. */
  def derivedImpl[T: Type](using Quotes): Expr[ScalaTypeInfo[T]] = {
    import quotes.reflect.*
    val tpe = TypeRepr.of[T]
    // the expressions are built here, outside of the quote, because the TypeRepr of this Quotes instance is not in scope inside it
    val modelClass = classExpr(tpe)
    val erasedPrimitives = erasedPrimitiveEntries(tpe)
    val subtypes = subtypeClasses(tpe)
    val related = Expr.ofList(reachableInfos(relatedTypes(tpe), Set(tpe.typeSymbol.fullName)))
    '{ new ScalaTypeInfo[T]($modelClass, Map($erasedPrimitives*), $subtypes, $related) }
  }

  def registerAllImpl[T <: Tuple: Type](using Quotes): Expr[Unit] = {
    import quotes.reflect.*

    // a tuple type is either a TupleN, whose type arguments are the types to register, or a `head *: tail` chain ended by EmptyTuple
    def tupleTypes(tpe: TypeRepr): List[TypeRepr] = tpe.dealias match {
      case AppliedType(tycon, List(head, tail)) if tycon.typeSymbol.name == "*:" => head :: tupleTypes(tail)
      case other => other.typeArgs
    }

    Expr.block(registrations(tupleTypes(TypeRepr.of[T])), '{ () })
  }

  /** A registration call for each of these types and for the types reachable from them. */
  private def registrations(using Quotes)(types: List[quotes.reflect.TypeRepr]): List[Expr[Unit]] = {
    reachableInfos(types, Set.empty).map(info => '{ ScalaModelRegistry.registerType($info) })
  }

  /** The type information of each of these types and of the types reachable from them. */
  private def reachableInfos(using
      Quotes
  )(types: List[quotes.reflect.TypeRepr], alreadyVisited: Set[String]): List[Expr[ScalaTypeInfo[?]]] = {
    import quotes.reflect.*

    val infos = List.newBuilder[Expr[ScalaTypeInfo[?]]]
    val visited = collection.mutable.Set.from(alreadyVisited)

    def visit(tpe: TypeRepr): Unit = {
      val dealiased = tpe.dealias
      if (isModelType(dealiased) && visited.add(dealiased.typeSymbol.fullName)) {
        infos += typeInfoExpr(dealiased)
        relatedTypes(dealiased).foreach(visit)
      }
    }

    types.foreach(visit)
    infos.result()
  }

  private def typeInfoExpr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[ScalaTypeInfo[?]] = {
    // the expressions are built here, outside of the quote, because the TypeRepr of this Quotes instance is not in scope inside it
    val modelClass = classExpr(tpe)
    val erasedPrimitives = erasedPrimitiveEntries(tpe)
    val subtypes = subtypeClasses(tpe)
    tpe.asType match {
      case '[t] => '{ new ScalaTypeInfo[t]($modelClass, Map($erasedPrimitives*), $subtypes) }
    }
  }

  /** The name and erased primitive type of each field that has one. */
  private def erasedPrimitiveEntries(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[List[(String, Class[?])]] = {
    Expr.ofList(fieldsOf(classTypeOf(tpe)).flatMap { case (name, fieldType) =>
      erasedPrimitiveOf(fieldType).map { primitive =>
        val nameExpr = Expr(name)
        val primitiveExpr = classExpr(primitive)
        '{ Tuple2($nameExpr, $primitiveExpr) }
      }
    })
  }

  /** The classes of the direct subtypes of a sealed trait or class - a `case object` is represented by its module class. */
  private def subtypeClasses(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[List[Class[?]]] = {
    import quotes.reflect.*
    Expr.ofList(
      tpe.typeSymbol.children
        .map(child => if (child.flags.is(Flags.Module)) child.moduleClass.typeRef else child.typeRef)
        .sortBy(_.typeSymbol.fullName)
        .map(classExpr)
    )
  }

  /** `classOf[tpe]` - `Literal(ClassOfConstant(...))` is used because `classOf` needs a class type known to the compiler. */
  private def classExpr(using Quotes)(tpe: quotes.reflect.TypeRepr): Expr[Class[?]] = {
    import quotes.reflect.*
    Literal(ClassOfConstant(tpe)).asExprOf[Class[?]]
  }

  /** The type to read the fields of a class from. The registry is keyed by class, and a class has one class for all of its type arguments,
    * so only what holds for every instantiation of a generic class can be recorded: the type reference of the class itself leaves its type
    * parameters abstract, which drops the fields whose element type is one of them. `Seq[T]` is as unknowable at runtime as it is on Scala
    * 2, whereas `Seq[Long]` of a `class LongSeq extends Seq[Long]` is still resolved, because that is true of the class.
    */
  private def classTypeOf(using Quotes)(tpe: quotes.reflect.TypeRepr): quotes.reflect.TypeRepr = {
    if (tpe.typeArgs.isEmpty) tpe else tpe.typeSymbol.typeRef
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
    val primitives = Seq(
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

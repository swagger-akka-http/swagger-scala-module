package com.github.swagger.scala.converter

import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

/** The type information of a Scala 3 class that the JVM does not keep: the primitive types that the element types of its `Option`, `Map` and
  * collection fields have been erased to, and the subtypes of a sealed trait or class.
  *
  * Derive it on a model class to make that information available to the converter:
  * {{{
  * case class Pet(name: String, age: Option[Int]) derives ScalaTypeInfo
  * }}}
  *
  * The `derives` clause puts a given instance in the companion object of the class, which the converter looks up when it first generates a
  * schema for that class - nothing else needs to be called. The instance also carries the type information of the types reachable from the
  * class, in [[related]], so deriving this on the top level model classes of an API usually covers the whole model.
  * [[ScalaModelRegistry.register]] does the same thing for classes that cannot be given a `derives` clause, such as those of a third party
  * library.
  *
  * Types with type parameters cannot be derived usefully: the compiler asks for a `ScalaTypeInfo` of each type parameter, and the element
  * types of a generic field are not known for the class as a whole anyway.
  *
  * @since v2.16.0
  */
final class ScalaTypeInfo[T](
    val modelClass: Class[?],
    val erasedPrimitives: Map[String, Class[?]],
    val subtypes: Seq[Class[?]],
    val related: Seq[ScalaTypeInfo[?]] = Nil
) {
  override def toString: String =
    s"ScalaTypeInfo(${modelClass.getName}, erasedPrimitives=$erasedPrimitives, subtypes=${subtypes.map(_.getName).mkString("[", ", ", "]")})"
}

object ScalaTypeInfo {

  /** Reads the type information of `T`, and of the types reachable from `T`, at compile time. */
  inline def derived[T]: ScalaTypeInfo[T] = ${ ScalaModelRegistryMacros.derivedImpl[T] }

  private val logger = LoggerFactory.getLogger(ScalaTypeInfo.getClass)

  /** Looks up the instance that a `derives ScalaTypeInfo` clause put in the companion object of this class. */
  private[converter] def findDerived(cls: Class[?]): Option[ScalaTypeInfo[?]] = {
    try {
      val className = cls.getName
      val companionName = if (className.endsWith("$")) className else className + "$"
      val companion = Class.forName(companionName, true, classLoader(cls))
      val module = companion.getField("MODULE$").get(null)
      companion.getMethods
        .find(method => method.getParameterCount == 0 && classOf[ScalaTypeInfo[?]].isAssignableFrom(method.getReturnType))
        .map(_.invoke(module).asInstanceOf[ScalaTypeInfo[?]])
        .filter(_.modelClass == cls)
    } catch {
      case _: ClassNotFoundException | _: NoSuchFieldException => None // no companion object, so nothing was derived
      case NonFatal(t) =>
        logger.debug(s"Unable to read the derived ScalaTypeInfo of ${cls.getName}", t)
        None
      case err: NoClassDefFoundError =>
        logger.debug(s"Unable to read the derived ScalaTypeInfo of ${cls.getName}", err)
        None
    }
  }

  private def classLoader(cls: Class[?]): ClassLoader =
    Option(cls.getClassLoader).getOrElse(Thread.currentThread().getContextClassLoader)
}

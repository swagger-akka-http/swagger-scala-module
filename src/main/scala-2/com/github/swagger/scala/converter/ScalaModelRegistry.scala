package com.github.swagger.scala.converter

/** The Scala 2 counterpart of the Scala 3 `ScalaModelRegistry`, so that cross built code compiles against both.
  *
  * All methods are no-ops here: Scala 2 keeps the type information that Scala 3 has to capture at compile time in the `ScalaSignature` of
  * the class file, where `scala-reflect` reads it at runtime.
  *
  * @since v2.16.0
  */
object ScalaModelRegistry {

  /** Does nothing on Scala 2 - the type information of `T` is already available at runtime. */
  def register[T]: Unit = ()

  /** Does nothing on Scala 2 - the type information of `T` is already available at runtime. */
  def registerAll[T]: Unit = ()

  /** @return true - the type information of any class is available at runtime on Scala 2 */
  def isRegistered(cls: Class[_]): Boolean = true

  /** @return an empty set - nothing needs to be registered on Scala 2 */
  def registeredClasses: Set[Class[_]] = Set.empty

  /** Does nothing on Scala 2. */
  def clear(): Unit = ()
}

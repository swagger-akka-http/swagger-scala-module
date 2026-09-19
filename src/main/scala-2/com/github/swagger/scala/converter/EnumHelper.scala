package com.github.swagger.scala.converter

/** Scala 3 enums do not exist on Scala 2, so nothing here is ever an enum. See the scala-3 source directory for the real implementation.
  */
object EnumHelper {
  def isScala3Enum(cls: Class[_]): Boolean = false

  def scala3EnumValues(cls: Class[_]): Seq[String] = Seq.empty
}

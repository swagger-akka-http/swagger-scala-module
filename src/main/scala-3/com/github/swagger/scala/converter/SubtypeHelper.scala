package com.github.swagger.scala.converter

object SubtypeHelper {

  /** @return
    *   the direct subtypes of a sealed trait or sealed class. Scala 3 does not record sealed hierarchies in the class file, so they are
    *   known for the classes passed to [[ScalaModelRegistry.register]] and for those alone.
    */
  def findSubtypes(cls: Class[?]): Seq[Class[?]] = {
    ScalaModelRegistry.subtypes(cls).getOrElse {
      if (MissingTypeInfo.couldBeSealed(cls)) MissingTypeInfo.warnOnce(cls)
      Seq.empty
    }
  }
}

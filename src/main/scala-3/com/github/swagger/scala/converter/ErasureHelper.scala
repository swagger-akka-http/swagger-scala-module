package com.github.swagger.scala.converter

private[converter] object ErasureHelper {

  /** @return
    *   the primitive type that each `Option`, `Map` or collection field of this class has been erased to, keyed by field name. Scala 3
    *   keeps this information in TASTy only, so it is available for the classes passed to [[ScalaModelRegistry.register]] and for those
    *   alone.
    */
  def erasedOptionalPrimitives(cls: Class[?]): Map[String, Class[?]] = {
    ScalaModelRegistry.erasedPrimitives(cls).getOrElse {
      if (MissingTypeInfo.hasErasedGenericFields(cls)) MissingTypeInfo.warnOnce(cls)
      Map.empty[String, Class[?]]
    }
  }
}

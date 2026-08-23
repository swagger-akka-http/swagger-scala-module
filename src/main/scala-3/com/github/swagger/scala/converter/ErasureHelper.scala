package com.github.swagger.scala.converter

private[converter] object ErasureHelper {

  /** @return
    *   the primitive type that each `Option`, `Map` or collection field of this class has been erased to, keyed by field name. Scala 3
    *   keeps this information in TASTy only, so it is available for the classes that derive [[ScalaTypeInfo]] or that have been passed to
    *   [[ScalaModelRegistry.register]], and for those alone.
    */
  def erasedOptionalPrimitives(cls: Class[?]): Map[String, Class[?]] = {
    ScalaModelRegistry.infoFor(cls) match {
      case Some(info) => info.erasedPrimitives
      case None =>
        MissingTypeInfo.warnIfErased(cls)
        Map.empty[String, Class[?]]
    }
  }
}

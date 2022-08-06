package com.github.swagger.scala.converter

object ErasureHelper {

  def erasedOptionalPrimitives(cls: Class[_]): Map[String, Class[_]] = {
    import scala.reflect.runtime.universe
    val mirror = universe.runtimeMirror(cls.getClassLoader)
    val sym = mirror.staticClass(cls.getName)
    val properties = sym.selfType.members
      .filterNot(_.isMethod)
      .filterNot(_.isClass)

    properties.flatMap { prop =>
      val maybeClass: Option[Class[_]] = prop.typeSignature.typeArgs.headOption.flatMap { signature =>
        if (signature.typeSymbol.isClass) {
          Option(mirror.runtimeClass(signature.typeSymbol.asClass))
        } else None
      }
      maybeClass.map(prop.name.toString.trim -> _)
    }.toMap
  }

}

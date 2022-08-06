package com.github.swagger.scala.converter

import io.swagger.v3.core.util.PrimitiveType
import io.swagger.v3.oas.models.media.Schema

object ErasureHelper {

  def erasedOptionalPrimitives(cls: Class[_]): Map[String, Schema[_]] = {
    import scala.reflect.runtime.universe
    val mirror = universe.runtimeMirror(cls.getClassLoader)
    val sym = mirror.staticClass(cls.getName)
    val properties = sym.selfType.members
      .filterNot(_.isMethod)
      .filterNot(_.isClass)
      .map(prop => prop.name.toString.trim -> prop.typeSignature).toMap

    properties.mapValues { typeSignature =>
      if (typeSignature.typeSymbol.isClass && mirror.runtimeClass(typeSignature.typeSymbol.asClass) != classOf[scala.Option[_]]) {
        None
      } else {
        val typeArg = typeSignature.typeArgs.headOption
        typeArg.flatMap { signature =>
          if (signature.typeSymbol.isClass) {
            val runtimeClass = mirror.runtimeClass(signature.typeSymbol.asClass)
            Option(PrimitiveType.fromType(runtimeClass)).map(_.createProperty())
          } else None
        }
      }
    }.collect { case (k, Some(v)) => k -> v }.toMap
  }

}

package com.github.swagger.scala.converter

import scala.reflect.runtime.universe
import scala.util.Try

private[converter] object ErasureHelper {

  def erasedOptionalPrimitives(cls: Class[_]): Map[String, Class[_]] = {
    val mirror = universe.runtimeMirror(cls.getClassLoader)

    val moduleSymbol = mirror.moduleSymbol(Class.forName(cls.getName))
    val ConstructorName = "apply"
    val companion: universe.Symbol = moduleSymbol.typeSignature.member(universe.TermName(ConstructorName))
    val properties =
      Try(companion.asTerm.alternatives.head.asMethod.paramLists.flatten).getOrElse {
        val sym = mirror.staticClass(cls.getName)
        sym.selfType.members
          .filterNot(_.isMethod)
          .filterNot(_.isClass)
      }

    properties.flatMap { prop: universe.Symbol =>
      val maybeClass: Option[Class[_]] = prop.typeSignature.typeArgs.headOption.flatMap { signature =>
        if (signature.typeSymbol.isClass) {
          Option(mirror.runtimeClass(signature.typeSymbol.asClass))
        } else None
      }
      maybeClass.map(prop.name.toString.trim -> _)
    }.toMap
  }

}

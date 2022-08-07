package com.github.swagger.scala.converter

import org.slf4j.LoggerFactory

import scala.reflect.runtime.universe
import scala.util.Try
import scala.util.control.NonFatal

private[converter] object ErasureHelper {
  private val logger = LoggerFactory.getLogger(ErasureHelper.getClass)

  def erasedOptionalPrimitives(cls: Class[_]): Map[String, Class[_]] = {
    try {
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
    } catch {
      case NonFatal(t) => {
        if (logger.isDebugEnabled) {
          logger.debug("Unable to get type info {}", Option(cls.getName).getOrElse("null"), t)
        } else {
          logger.info("Unable to get type info {}", Option(cls.getName).getOrElse("null"))
        }
        Map.empty[String, Class[_]]
      }
    }
  }
}

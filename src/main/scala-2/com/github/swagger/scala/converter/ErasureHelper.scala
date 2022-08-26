package com.github.swagger.scala.converter

import org.slf4j.LoggerFactory

import scala.annotation.tailrec
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
            signature.typeArgs.headOption match {
              case Some(typeArg) => Option(mirror.runtimeClass(nestedTypeArg(typeArg)))
              case _ => Option(mirror.runtimeClass(signature))
            }
          } else {
            None
          }
        }
        maybeClass.map(prop.name.toString.trim -> _)
      }.toMap
    } catch {
      case NonFatal(t) => {
        if (logger.isDebugEnabled) {
          // use this form because of Scala 2.11 & 2.12 compile issue
          logger.debug(s"Unable to get type info ${Option(cls.getName).getOrElse("null")}", t)
        } else {
          logger.info("Unable to get type info {}", Option(cls.getName).getOrElse("null"))
        }
        Map.empty[String, Class[_]]
      }
    }
  }

  @tailrec
  private def nestedTypeArg(typeArg: universe.Type): universe.Type = {
    typeArg.typeArgs.headOption match {
      case Some(innerArg) => nestedTypeArg(innerArg)
      case _ => typeArg
    }
  }
}

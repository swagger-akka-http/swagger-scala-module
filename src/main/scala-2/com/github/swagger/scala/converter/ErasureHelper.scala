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
      val mirror = universe.runtimeMirror(Thread.currentThread().getContextClassLoader)
      val classSymbol = mirror.classSymbol(cls)
      val ConstructorName = "apply"
      val companion: universe.Symbol = classSymbol.typeSignature.member(universe.TermName(ConstructorName))
      val properties =
        Try(companion.asTerm.alternatives.head.asMethod.paramLists.flatten).getOrElse {
          classSymbol.selfType.members
            .filterNot(_.isMethod)
            .filterNot(_.isClass)
        }

      properties.flatMap { prop: universe.Symbol =>
        val maybeClass: Option[Class[_]] = prop.typeSignature.typeArgs.headOption.flatMap { signature =>
          if (signature.typeSymbol.isClass) {
            signature.typeArgs.headOption match {
              case Some(typeArg) => {
                val resultType: universe.Type = nestedTypeArg(typeArg)
                val resultClass = mirror.runtimeClass(resultType)
                if (resultClass.isPrimitive) Option(resultClass) else None
              }
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
          logger.info(s"Unable to get type info ${Option(cls.getName).getOrElse("null")}: $t")
        }
        Map.empty[String, Class[_]]
      }
      case err: NoClassDefFoundError => {
        if (logger.isDebugEnabled) {
          // use this form because of Scala 2.11 & 2.12 compile issue
          logger.debug(s"Unable to get type info ${Option(cls.getName).getOrElse("null")}", err)
        } else {
          logger.info(s"Unable to get type info ${Option(cls.getName).getOrElse("null")}: $err")
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

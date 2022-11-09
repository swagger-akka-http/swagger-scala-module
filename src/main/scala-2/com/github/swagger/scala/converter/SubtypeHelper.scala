package com.github.swagger.scala.converter

import org.slf4j.LoggerFactory

import scala.reflect.runtime.{universe => ru}
import scala.util.control.NonFatal

object SubtypeHelper {
  private val logger = LoggerFactory.getLogger(SubtypeHelper.getClass)

  def findSubtypes(cls: Class[_]): Seq[Class[_]] = {
    try {
      val mirror = ru.runtimeMirror(Thread.currentThread().getContextClassLoader)
      val classSymbol = mirror.classSymbol(cls)
      lazy val symbol = companionOrSelf(classSymbol)
      if (classSymbol.isJava) {
        Seq.empty
      } else if (symbol.isClass) {
        symbol.asClass.knownDirectSubclasses.toSeq
          .sortBy(_.info.toString)
          .flatMap(s => if (s.isClass) Some(s.asClass) else None)
          .map(c => mirror.runtimeClass(c))
      } else {
        Seq.empty
      }
    } catch {
      case NonFatal(t) => {
        logger.warn(s"Failed to findSubtypes in $cls", t)
        Seq.empty
      }
    }
  }

  private def companionOrSelf(sym: ru.Symbol): ru.Symbol = {
    if (sym.companion == ru.NoSymbol) sym else sym.companion
  }
}

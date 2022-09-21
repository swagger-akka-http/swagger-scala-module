package com.github.swagger.scala.converter

import co.blocke.scala_reflection.RType
import co.blocke.scala_reflection.info.{ObjectInfo, ScalaClassInfo, SealedTraitInfo}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object SubtypeHelper {
  private val logger = LoggerFactory.getLogger(SubtypeHelper.getClass())

  def findSubtypes(cls: Class[_]): Seq[Class[_]] = {
    try {
      val rtype = RType.of(cls)
      rtype match {
        case traitInfo: SealedTraitInfo =>
          traitInfo.children
            .map(ct => getClass(ct))
            .toSeq
        case classInfo: ScalaClassInfo =>
          classInfo.children
            .map(ct => getClass(ct))
            .toSeq
        case _ => Seq.empty
      }
    } catch {
      case NonFatal(t) => {
        logger.warn(s"Failed to findSubtypes in ${cls.getName}: $t")
        Seq.empty
      }
    }
  }

  private def getClass(rtype: RType): Class[_] = rtype match {
    case objectInfo: ObjectInfo => getCompanionObjectClass(objectInfo.infoClass)
    case rt => rt.infoClass
  }

  private def getCompanionObjectClass(cls: Class[_]): Class[_] = {
    val cn = cls.getName
    if (cn.endsWith("$")) {
      cls
    } else {
      Class.forName(cn + '$', true, Thread.currentThread().getContextClassLoader)
    }
  }
}

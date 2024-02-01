package com.github.swagger.scala.converter

import co.blocke.scala_reflection.RType
import co.blocke.scala_reflection.impl.CollectionRType
import co.blocke.scala_reflection.info.{ClassInfo, MapLikeInfo, ScalaOptionInfo}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec
import scala.util.control.NonFatal

private[converter] object ErasureHelper {
  private val logger = LoggerFactory.getLogger(ErasureHelper.getClass)

  def erasedOptionalPrimitives(cls: Class[_]): Map[String, Class[_]] = {
    try {
      val rType = RTypeCache.getRType(cls)
      rType match {
        case classInfo: ClassInfo => {
          val results = classInfo.fields.flatMap { fieldInfo =>
            fieldInfo.fieldType match {
              case optionInfo: ScalaOptionInfo =>
                val innerClass = getInnerType(optionInfo.optionParamType).infoClass
                if (innerClass.isPrimitive) Some(fieldInfo.name -> innerClass) else None
              case mapInfo: MapLikeInfo =>
                val innerClass = getInnerType(mapInfo.elementType2).infoClass
                if (innerClass.isPrimitive) Some(fieldInfo.name -> innerClass) else None
              case seqInfo: CollectionRType =>
                val innerClass = getInnerType(seqInfo.elementType).infoClass
                if (innerClass.isPrimitive) Some(fieldInfo.name -> innerClass) else None
              case _ =>
                None
            }
          }
          results.toMap
        }
        case _ => Map.empty[String, Class[_]]
      }
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

  @tailrec
  private def getInnerType(rtype: RType): RType = rtype match {
    case optionInfo: ScalaOptionInfo => getInnerType(optionInfo.optionParamType)
    case mapInfo: MapLikeInfo => getInnerType(mapInfo.elementType2)
    case seqInfo: CollectionRType => getInnerType(seqInfo.elementType)
    case _ => rtype
  }
}

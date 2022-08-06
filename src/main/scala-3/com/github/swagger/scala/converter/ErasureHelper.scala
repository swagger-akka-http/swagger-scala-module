package com.github.swagger.scala.converter

import co.blocke.scala_reflection.RType
import co.blocke.scala_reflection.impl.CollectionRType
import co.blocke.scala_reflection.info.{ClassInfo, MapLikeInfo, ScalaOptionInfo}
import org.slf4j.LoggerFactory

import scala.annotation.tailrec

object ErasureHelper {
  private val logger = LoggerFactory.getLogger(ErasureHelper.getClass)

  def erasedOptionalPrimitives(cls: Class[_]): Map[String, Class[_]] = {
    try {
      val rType = RType.of(cls)
      rType match {
        case classInfo: ClassInfo => {
          val results = classInfo.fields.flatMap { fieldInfo =>
            fieldInfo.fieldType match {
              case optionInfo: ScalaOptionInfo =>
                Some(fieldInfo.name -> getInnerType(optionInfo.optionParamType).infoClass)
              case mapInfo: MapLikeInfo =>
                Some(fieldInfo.name -> getInnerType(mapInfo.elementType2).infoClass)
              case seqInfo: CollectionRType =>
                Some(fieldInfo.name -> getInnerType(seqInfo.elementType).infoClass)
              case _ =>
                None
            }
          }
          results.toMap
        }
        case _ => Map.empty[String, Class[_]]
      }
    } catch {
      case t: Throwable => {
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

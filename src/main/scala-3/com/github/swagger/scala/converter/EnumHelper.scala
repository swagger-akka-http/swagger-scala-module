package com.github.swagger.scala.converter

import org.slf4j.LoggerFactory

import java.lang.reflect.InvocationTargetException
import scala.reflect.Enum
import scala.util.Try
import scala.util.control.NonFatal

/** Runtime support for Scala 3 `enum` types, merged in from swagger-scala3-enum-module. */
object EnumHelper {
  private val logger = LoggerFactory.getLogger(EnumHelper.getClass)
  private val EnumClass = classOf[Enum]
  private val IntClass = classOf[Int]

  def isScala3Enum(cls: Class[_]): Boolean = EnumClass.isAssignableFrom(cls)

  /** The names of the singleton cases of a Scala 3 enum, in ordinal order. Parameterized cases (`case Mix(rgb: Int)`) have no fixed name
    * and are left out.
    */
  def scala3EnumValues(cls: Class[_]): Seq[String] = {
    try {
      valuesFromCompanion(cls).getOrElse(valuesFromOrdinals(cls))
    } catch {
      case NonFatal(t) => {
        logger.warn(s"Failed to get values for enum ${cls.getName}", t)
        Seq.empty
      }
    }
  }

  /** `values` is only generated for enums whose cases are all singletons. */
  private def valuesFromCompanion(cls: Class[_]): Option[Seq[String]] = {
    Try {
      val companion = companionObject(cls)
      val values = companion.getClass.getDeclaredMethod("values").invoke(companion).asInstanceOf[Array[Enum]]
      values.sortBy(_.ordinal).map(_.toString).toSeq
    }.toOption
  }

  /** `fromOrdinal` exists on every enum companion and throws NoSuchElementException once the ordinal is past the singleton cases. */
  private def valuesFromOrdinals(cls: Class[_]): Seq[String] = {
    val companion = companionObject(cls)
    val fromOrdinal = companion.getClass.getMethod("fromOrdinal", IntClass)
    val matched = Seq.newBuilder[String]
    var i = 0
    var complete = false
    while (!complete) {
      try {
        matched += fromOrdinal.invoke(companion, i).toString
      } catch {
        case _: NoSuchElementException => complete = true
        case itex: InvocationTargetException => {
          itex.getCause match {
            case _: NoSuchElementException => complete = true
            case null => throw itex
            case cause => throw cause
          }
        }
      }
      i += 1
    }
    matched.result()
  }

  private def companionObject(cls: Class[_]): AnyRef = {
    val companionClass = Class.forName(cls.getName + "$", true, Thread.currentThread.getContextClassLoader)
    companionClass.getField("MODULE$").get(None.orNull)
  }
}

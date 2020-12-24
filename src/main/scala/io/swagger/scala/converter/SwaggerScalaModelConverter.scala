package io.swagger.scala.converter

import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.util.Iterator
import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.swagger.converter._
import io.swagger.models.Model
import io.swagger.models.properties._
import io.swagger.util.{Json, PrimitiveType}
import org.slf4j.LoggerFactory

import scala.util.control.NonFatal

object SwaggerScalaModelConverter {
  Json.mapper().registerModule(DefaultScalaModule)
}

class SwaggerScalaModelConverter extends ModelConverter {
  SwaggerScalaModelConverter

  private val logger = LoggerFactory.getLogger(classOf[SwaggerScalaModelConverter])
  private val OptionClass = classOf[scala.Option[_]]
  private val BigDecimalClass = classOf[BigDecimal]
  private val BigIntClass = classOf[BigInt]

  override
  def resolveProperty(`type`: Type, context: ModelConverterContext,
    annotations: Array[Annotation] , chain: Iterator[ModelConverter]): Property = {
    val javaType = Json.mapper().constructType(`type`)
    val cls = javaType.getRawClass

    if(cls != null) {
      if (isEnumerationInstance(javaType.getRawClass)) {
        val sp = new StringProperty()
        sp.setRequired(true)
        try {
          val valueMethods = cls.getMethods.toSeq.filter { m =>
            m.getReturnType.getName == "scala.Enumeration$Value" && m.getParameterCount == 0
          }
          val enumValues = valueMethods.map(_.getName).filterNot(_ == "Value")
          enumValues.foreach { v =>
            sp._enum(v)
          }
        } catch {
          case NonFatal(t) => logger.warn(s"Failed to get values for enum ${cls.getName}", t)
        }
        return sp
      } else {
        if (cls == BigDecimalClass) {
          val dp = PrimitiveType.DECIMAL.createProperty()
          dp.setRequired(true)
          return dp
        } else if (cls == BigIntClass) {
          val dp = PrimitiveType.INT.createProperty()
          dp.setRequired(true)
          return dp
        }
      }
    }

    // Unbox scala options
    `type` match {
      case rt: ReferenceType if isOption(cls) =>
        val nextType = rt.getContentType
        val nextResolved = {
          Option(resolveProperty(nextType, context, annotations, chain)) match {
            case Some(p) => Some(p)
            case None if chain.hasNext =>
              Option(chain.next().resolveProperty(nextType, context, annotations, chain))
            case _ => None
          }
        }
        nextResolved match {
          case Some(property) => {
            property.setRequired(false)
            property
          }
          case None => None.orNull
        }
      case t if chain.hasNext =>
        val nextResolved = Option(chain.next().resolveProperty(t, context, annotations, chain))
        nextResolved match {
          case Some(property) => {
            property.setRequired(true)
            property
          }
          case None => None.orNull
        }
      case _ =>
        None.orNull
    }
  }

  override
  def resolve(`type`: Type, context: ModelConverterContext, chain: Iterator[ModelConverter]): Model = {
    val javaType = Json.mapper().constructType(`type`)
    if (isEnumerationInstance(javaType.getRawClass)) {
      null // ignore scala enums
    } else {
      if (chain.hasNext()) {
        val next = chain.next()
        next.resolve(`type`, context, chain)
      }
      else
        null
    }
  }

  private def isEnumerationInstance(cls: Class[_]): Boolean =
    cls.getFields.map(_.getName).contains("MODULE$")

  private def isOption(cls: Class[_]): Boolean = cls == OptionClass
}

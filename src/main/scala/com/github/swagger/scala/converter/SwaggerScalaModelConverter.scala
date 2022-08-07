package com.github.swagger.scala.converter

import java.lang.annotation.Annotation
import java.lang.reflect.ParameterizedType
import com.fasterxml.jackson.databind.{JavaType, ObjectMapper}
import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.module.scala.introspect.{BeanIntrospector, PropertyDescriptor}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, JsonScalaEnumeration}
import io.swagger.v3.core.converter._
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.{Json, PrimitiveType}
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.{ArraySchema, Schema => SchemaAnnotation}
import io.swagger.v3.oas.models.media.Schema
import org.slf4j.LoggerFactory

import java.util
import scala.collection.Seq
import scala.util.Try
import scala.util.control.NonFatal

class AnnotatedTypeForOption extends AnnotatedType

object SwaggerScalaModelConverter {
  val objectMapper: ObjectMapper = Json.mapper().registerModule(DefaultScalaModule)
}

class SwaggerScalaModelConverter extends ModelResolver(SwaggerScalaModelConverter.objectMapper) {

  private val logger = LoggerFactory.getLogger(classOf[SwaggerScalaModelConverter])
  private val VoidClass = classOf[Void]
  private val EnumClass = classOf[scala.Enumeration]
  private val OptionClass = classOf[scala.Option[_]]
  private val IterableClass = classOf[scala.collection.Iterable[_]]
  private val MapClass = classOf[Map[_, _]]
  private val SetClass = classOf[scala.collection.Set[_]]
  private val BigDecimalClass = classOf[BigDecimal]
  private val BigIntClass = classOf[BigInt]
  private val ProductClass = classOf[Product]
  private val AnyClass = classOf[Any]

  override def resolve(`type`: AnnotatedType, context: ModelConverterContext, chain: util.Iterator[ModelConverter]): Schema[_] = {
    val javaType = _mapper.constructType(`type`.getType)
    val cls = javaType.getRawClass

    matchScalaPrimitives(`type`, cls).getOrElse {
      // Unbox scala options
      val annotatedOverrides = getRequiredSettings(`type`)
      if (_isOptional(`type`, cls)) {
        val baseType = if (annotatedOverrides.headOption.getOrElse(false)) new AnnotatedType() else new AnnotatedTypeForOption()
        resolve(nextType(baseType, `type`, javaType), context, chain)
      } else if (!annotatedOverrides.headOption.getOrElse(true)) {
        resolve(nextType(new AnnotatedTypeForOption(), `type`, javaType), context, chain)
      } else if (isCaseClass(cls)) {
        caseClassSchema(cls, `type`, context, chain).getOrElse(None.orNull)
      } else if (chain.hasNext) {
        val nextResolved = Option(chain.next().resolve(`type`, context, chain))
        nextResolved match {
          case Some(property) => {
            if (isIterable(cls)) {
              property.setRequired(null)
              property.setProperties(null)
            }
            setRequired(`type`)
            property
          }
          case None => None.orNull
        }
      } else {
        None.orNull
      }
    }
  }

  private def caseClassSchema(cls: Class[_], `type`: AnnotatedType, context: ModelConverterContext,
                              chain: util.Iterator[ModelConverter]): Option[Schema[_]] = {
    if (chain.hasNext) {
      Option(chain.next().resolve(`type`, context, chain)).map { schema =>
        val introspector = BeanIntrospector(cls)
        val erasedProperties = ErasureHelper.erasedOptionalPrimitives(cls)
        introspector.properties.foreach { property =>
          val (propertyClass, propertyAnnotations) = getPropertyClassAndAnnotations(property)
          val isOptional = isOption(propertyClass)
          val schemaOverrideClass = propertyAnnotations.collectFirst {
            case s: SchemaAnnotation if s.implementation() != VoidClass => s.implementation()
          }
          val propertyName = property.name
          if (schema.getProperties != null && schemaOverrideClass.isEmpty) {
            erasedProperties.get(propertyName).foreach { erasedType =>
              val primitiveType = PrimitiveType.fromType(erasedType)
              val property      = schema.getProperties.get(propertyName)
              if (primitiveType != null && property != null) {
                if (isOptional) {
                  schema.addProperty(propertyName, correctSchema(property, primitiveType))
                }
                if (isIterable(propertyClass) && !isMap(propertyClass)) {
                  schema.addProperty(propertyName, updateTypeOnItemsSchema(primitiveType, property))
                }
              }
            }
          }
          propertyAnnotations match {
            case Seq() => {
              if (isOptional && schema.getRequired != null && schema.getRequired.contains(property.name)) {
                schema.getRequired.remove(property.name)
              } else if (!isOptional) {
                addRequiredItem(schema, property.name)
              }
            }
            case annotations => {
              val required = getRequiredSettings(annotations).headOption
                .getOrElse(!isOptional)
              if (required) addRequiredItem(schema, property.name)
            }
          }
        }
        schema
      }
    } else {
      None
    }
  }

  private def updateTypeOnItemsSchema(primitiveType: PrimitiveType, propertySchema: Schema[_]) = {
    val updatedSchema = correctSchema(propertySchema.getItems, primitiveType)
    propertySchema.setItems(updatedSchema)
    propertySchema
  }

  private def correctSchema(itemSchema: Schema[_], primitiveType: PrimitiveType) = {
    val primitiveProperty = primitiveType.createProperty()
    val propAsString = objectMapper.writeValueAsString(itemSchema)
    val correctedSchema = objectMapper.readValue(propAsString, primitiveProperty.getClass)
    correctedSchema.setType(primitiveProperty.getType)
    if (itemSchema.getFormat == null) {
      correctedSchema.setFormat(primitiveProperty.getFormat)
    }
    correctedSchema
  }

  private def getRequiredSettings(annotatedType: AnnotatedType): Seq[Boolean] = annotatedType match {
    case _: AnnotatedTypeForOption => Seq.empty
    case _ => getRequiredSettings(nullSafeList(annotatedType.getCtxAnnotations))
  }

  private def getRequiredSettings(annotations: Seq[Annotation]): Seq[Boolean] = {
    annotations.collect {
      case p: Parameter => p.required()
      case s: SchemaAnnotation => s.required()
      case a: ArraySchema => a.arraySchema().required()
    }
  }

  private def matchScalaPrimitives(`type`: AnnotatedType, nullableClass: Class[_]): Option[Schema[_]] = {
    val annotations = Option(`type`.getCtxAnnotations).map(_.toSeq).getOrElse(Seq.empty)
    annotations.collectFirst { case ann: SchemaAnnotation => ann } match {
      case Some(_) => None
      case _ => {
        annotations.collectFirst { case ann: JsonScalaEnumeration => ann } match {
          case Some(enumAnnotation: JsonScalaEnumeration) => {
            val pt = enumAnnotation.value().getGenericSuperclass.asInstanceOf[ParameterizedType]
            val args = pt.getActualTypeArguments
            val cls = args(0).asInstanceOf[Class[_]]
            val sp: Schema[String] = PrimitiveType.STRING.createProperty().asInstanceOf[Schema[String]]
            setRequired(`type`)
            try {
              val mainClass = getMainClass(cls)
              val valueMethods = mainClass.getMethods.toSeq.filter { m =>
                m.getDeclaringClass != EnumClass &&
                  m.getReturnType.getName == "scala.Enumeration$Value" && m.getParameterCount == 0
              }
              val enumValues = valueMethods.map(_.invoke(None.orNull))
              enumValues.foreach { v =>
                sp.addEnumItemObject(v.toString)
              }
            } catch {
              case NonFatal(t) => logger.warn(s"Failed to get values for enum ${cls.getName}", t)
            }
            Some(sp)
          }
          case _ => {
            Option(nullableClass).flatMap { cls =>
              if (cls == BigDecimalClass) {
                val dp = PrimitiveType.DECIMAL.createProperty()
                setRequired(`type`)
                Some(dp)
              } else if (cls == BigIntClass) {
                val ip = PrimitiveType.INT.createProperty()
                setRequired(`type`)
                Some(ip)
              } else {
                None
              }
            }
          }
        }
      }
    }
  }

  private def getMainClass(clazz: Class[_]): Class[_] = {
    val cname = clazz.getName
    if (cname.endsWith("$")) {
      Try(Class.forName(cname.substring(0, cname.length - 1))).getOrElse(clazz)
    } else {
      clazz
    }
  }

  private def _isOptional(annotatedType: AnnotatedType, cls: Class[_]): Boolean = {
    annotatedType.getType match {
      case _: ReferenceType if isOption(cls) => true
      case _ => false
    }
  }

  private def underlyingJavaType(annotatedType: AnnotatedType, javaType: JavaType): JavaType = {
    annotatedType.getType match {
      case rt: ReferenceType => rt.getContentType
      case _ => javaType
    }
  }

  private def nextType(baseType: AnnotatedType, `type`: AnnotatedType, javaType: JavaType): AnnotatedType = {
    baseType.`type`(underlyingJavaType(`type`, javaType))
      .ctxAnnotations(`type`.getCtxAnnotations)
      .parent(`type`.getParent)
      .schemaProperty(`type`.isSchemaProperty)
      .name(`type`.getName)
      .propertyName(`type`.getPropertyName)
      .resolveAsRef(`type`.isResolveAsRef)
      .jsonViewAnnotation(`type`.getJsonViewAnnotation)
      .skipOverride(`type`.isSkipOverride)
  }

  override def _isOptionalType(propType: JavaType): Boolean = {
    isOption(propType.getRawClass) || super._isOptionalType(propType)
  }

  override def _isSetType(cls: Class[_]): Boolean = {
    val setInterfaces = cls.getInterfaces.find { interface =>
      interface == SetClass
    }
    setInterfaces.isDefined || super._isSetType(cls)
  }

  private def setRequired(annotatedType: AnnotatedType): Unit = annotatedType match {
    case _: AnnotatedTypeForOption => // not required
    case _ => {
      val required = getRequiredSettings(annotatedType).headOption.getOrElse(true)
      if (required) {
        Option(annotatedType.getParent).foreach { parent =>
          Option(annotatedType.getPropertyName).foreach { n =>
            addRequiredItem(parent, n)
          }
        }
      }
    }
  }

  private def getPropertyClassAndAnnotations(property: PropertyDescriptor): (Class[_], Seq[Annotation]) = {
    property.param match {
      case Some(constructorParameter) =>
        val types = constructorParameter.constructor.getParameterTypes
        val annotations = constructorParameter.constructor.getParameterAnnotations
        val index = constructorParameter.index
        if (index > types.size) {
          (AnyClass, Seq.empty)
        } else {
          val onlyType = types(index)
          val onlyAnnotations = if (index > annotations.size) { Seq.empty[Annotation] } else { annotations(index).toIndexedSeq }
          (onlyType, onlyAnnotations)
        }
      case _ => property.field match {
        case Some(field) => (field.getType, field.getAnnotations.toSeq)
        case _ => property.setter match {
          case Some(setter) if setter.getParameterCount == 1 => {
            (setter.getParameterTypes()(0), setter.getAnnotations.toSeq)
          }
          case _ => property.beanSetter match {
            case Some(setter) if setter.getParameterCount == 1 => {
              (setter.getParameterTypes()(0), setter.getAnnotations.toSeq)
            }
            case _ => (AnyClass, Seq.empty)
          }
        }
      }
    }
  }

  private def isOption(cls: Class[_]): Boolean = cls == OptionClass
  private def isIterable(cls: Class[_]): Boolean = IterableClass.isAssignableFrom(cls)
  private def isMap(cls: Class[_]): Boolean = MapClass.isAssignableFrom(cls)
  private def isCaseClass(cls: Class[_]): Boolean = ProductClass.isAssignableFrom(cls)

  private def nullSafeList[T](array: Array[T]): List[T] = Option(array) match {
    case None => List.empty[T]
    case Some(arr) => arr.toList
  }
}

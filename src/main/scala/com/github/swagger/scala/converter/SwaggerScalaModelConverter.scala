package com.github.swagger.scala.converter

import java.lang.annotation.Annotation
import java.lang.reflect.ParameterizedType
import java.util.Iterator
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.module.scala.introspect.{BeanIntrospector, PropertyDescriptor, ScalaAnnotationIntrospectorModule}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, JsonScalaEnumeration}
import io.swagger.v3.core.converter._
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.{Json, PrimitiveType}
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.{ArraySchema, Schema => SchemaAnnotation}
import io.swagger.v3.oas.models.media.{BooleanSchema, IntegerSchema, NumberSchema, ObjectSchema, Schema}
import org.slf4j.LoggerFactory

import java.math.BigInteger
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

class AnnotatedTypeForOption extends AnnotatedType

object SwaggerScalaModelConverter {
  val objectMapper = Json.mapper().registerModule(DefaultScalaModule)
}

class SwaggerScalaModelConverter extends ModelResolver(SwaggerScalaModelConverter.objectMapper) {
  SwaggerScalaModelConverter

  private val logger = LoggerFactory.getLogger(classOf[SwaggerScalaModelConverter])
  private val EnumClass = classOf[scala.Enumeration]
  private val OptionClass = classOf[scala.Option[_]]
  private val IterableClass = classOf[scala.collection.Iterable[_]]
  private val SetClass = classOf[scala.collection.Set[_]]
  private val BigDecimalClass = classOf[BigDecimal]
  private val BigIntClass = classOf[BigInt]
  private val ProductClass = classOf[Product]
  private val AnyClass = classOf[Any]

  override def resolve(`type`: AnnotatedType, context: ModelConverterContext, chain: Iterator[ModelConverter]): Schema[_] = {
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
                              chain: Iterator[ModelConverter]): Option[Schema[_]] = {
    if (chain.hasNext) {
      Option(chain.next().resolve(`type`, context, chain)).map { schema =>
        val introspector = BeanIntrospector(cls)
        val introspectorMap = introspector.properties.map { p =>
          (p.name, p)
        }.toMap
        val schemaProperties = schema.getProperties.asScala
        val newProperties = schemaProperties.map { case (propertyName, schemaProperty) =>
          introspectorMap.get(propertyName) match {
            case Some(introspectorProperty) => {
              getPropertyAnnotations(introspectorProperty) match {
                case Seq() => {
                  val propertyClass = getPropertyClass(introspectorProperty)
                  if (isOption(propertyClass) && schema.getRequired != null && schema.getRequired.contains(propertyName)) {
                    schema.getRequired.remove(propertyName)
                  }
                  if (!isOption(propertyClass)) addRequiredItem(schema, propertyName)
                }
                case annotations => {
                  val required = getRequiredSettings(annotations).headOption
                    .getOrElse(!isOption(getPropertyClass(introspectorProperty)))
                  if (required) addRequiredItem(schema, propertyName)
                }
              }
            }
            case _ =>
          }
          val updatedProperty = ScalaAnnotationIntrospectorModule.getRegisteredReferencedValueType(cls, propertyName) match {
            case Some(overrideType: Class[_]) => {
              schemaProperty match {
                case objectSchema: ObjectSchema => convertSchema(objectSchema, overrideType)
                case _ => schemaProperty
              }
            }
            case _ => schemaProperty
          }
          (propertyName, updatedProperty)
        }.toMap
        schema.setProperties(newProperties.asJava)
        schema
      }
    } else {
      None
    }
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

  private def getPropertyClass(property: PropertyDescriptor): Class[_] = {
    property.param match {
      case Some(constructorParameter) => {
        val types = constructorParameter.constructor.getParameterTypes
        if (constructorParameter.index > types.size) {
          AnyClass
        } else {
          types(constructorParameter.index)
        }
      }
      case _ => property.field match {
        case Some(field) => field.getType
        case _ => property.setter match {
          case Some(setter) if setter.getParameterCount == 1 => {
            setter.getParameterTypes()(0)
          }
          case _ => property.beanSetter match {
            case Some(setter) if setter.getParameterCount == 1 => {
              setter.getParameterTypes()(0)
            }
            case _ => AnyClass
          }
        }
      }
    }
  }

  private def getPropertyAnnotations(property: PropertyDescriptor): Seq[Annotation] = {
    property.param match {
      case Some(constructorParameter) => {
        val types = constructorParameter.constructor.getParameterAnnotations
        if (constructorParameter.index > types.size) {
          Seq.empty
        } else {
          types(constructorParameter.index).toSeq
        }
      }
      case _ => property.field match {
        case Some(field) => field.getAnnotations.toSeq
        case _ => property.setter match {
          case Some(setter) if setter.getParameterCount == 1 => {
            setter.getAnnotations().toSeq
          }
          case _ => property.beanSetter match {
            case Some(setter) if setter.getParameterCount == 1 => {
              setter.getAnnotations().toSeq
            }
            case _ => Seq.empty
          }
        }
      }
    }
  }

  private def isOption(cls: Class[_]): Boolean = cls == OptionClass
  private def isIterable(cls: Class[_]): Boolean = IterableClass.isAssignableFrom(cls)
  private def isCaseClass(cls: Class[_]): Boolean = ProductClass.isAssignableFrom(cls)

  private def nullSafeList[T](array: Array[T]): List[T] = Option(array) match {
    case None => List.empty[T]
    case Some(arr) => arr.toList
  }

  private def convertSchema(schema: ObjectSchema, formatClass: Class[_]): Schema[_] = {
    if (formatClass.isAssignableFrom(classOf[Boolean]) || formatClass.isAssignableFrom(classOf[java.lang.Boolean])) {
      convertToBooleanSchema(schema)
    } else if (formatClass.isAssignableFrom(classOf[Long]) || formatClass.isAssignableFrom(classOf[java.lang.Long])
      || formatClass.isAssignableFrom(BigIntClass) || formatClass.isAssignableFrom(classOf[BigInteger])) {
      convertToIntegerSchema(schema, true)
    } else if (formatClass.isAssignableFrom(classOf[Float]) || formatClass.isAssignableFrom(classOf[java.lang.Float])) {
      convertToNumberSchema(schema, false)
    } else if (formatClass.isAssignableFrom(classOf[Int]) || formatClass.isAssignableFrom(classOf[Integer])
      || formatClass.isAssignableFrom(classOf[Short]) || formatClass.isAssignableFrom(classOf[java.lang.Short])
      || formatClass.isAssignableFrom(classOf[Byte]) || formatClass.isAssignableFrom(classOf[java.lang.Byte])) {
      convertToIntegerSchema(schema, false)
    } else if (formatClass.isAssignableFrom(classOf[Number])) {
      convertToNumberSchema(schema, true)
    } else {
      schema
    }
  }

  private def convertToIntegerSchema(schema: ObjectSchema, longType: Boolean): IntegerSchema = {
    val format = if (longType) "int64" else "int32"
    val intSchema = new IntegerSchema()
    copySchemaSettings(schema, intSchema)
    intSchema.format(format)
    intSchema
  }

  private def convertToNumberSchema(schema: ObjectSchema, doubleType: Boolean): NumberSchema = {
    val format = if (doubleType) "double" else "float"
    val numSchema = new NumberSchema()
    copySchemaSettings(schema, numSchema)
    numSchema.format(format)
    numSchema
  }


  private def convertToBooleanSchema(schema: ObjectSchema): BooleanSchema = {
    val boolSchema = new BooleanSchema()
    copySchemaSettings(schema, boolSchema)
    boolSchema
  }

  private def copySchemaSettings(fromSchema: Schema[_], toSchema: Schema[_]): Unit = {
    toSchema
      .name(fromSchema.getName)
      .additionalProperties(fromSchema.getAdditionalProperties)
      .deprecated(fromSchema.getDeprecated)
      .description(fromSchema.getDescription)
      .discriminator(fromSchema.getDiscriminator)
      .example(fromSchema.getExample)
      .exclusiveMaximum(fromSchema.getExclusiveMaximum)
      .exclusiveMinimum(fromSchema.getExclusiveMinimum)
      .extensions(fromSchema.getExtensions)
      .externalDocs(fromSchema.getExternalDocs)
      .format(fromSchema.getFormat)
      .maximum(fromSchema.getMaximum)
      .minimum(fromSchema.getMinimum)
      .maxLength(fromSchema.getMaxLength)
      .minLength(fromSchema.getMinLength)
      .maxItems(fromSchema.getMaxItems)
      .minItems(fromSchema.getMinItems)
      .multipleOf(fromSchema.getMultipleOf)
      .not(fromSchema.getNot)
      .nullable(fromSchema.getNullable)
      .pattern(fromSchema.getPattern)
      .properties(fromSchema.getProperties)
      .readOnly(fromSchema.getReadOnly)
      .required(fromSchema.getRequired)
      .title(fromSchema.getTitle)
      .`type`(fromSchema.getType)
      .uniqueItems(fromSchema.getUniqueItems)
      .writeOnly(fromSchema.getWriteOnly)
      .xml(fromSchema.getXml)
      .$ref(fromSchema.get$ref())
    //ignores enum settings due to typing issues
    toSchema.setExampleSetFlag(fromSchema.getExampleSetFlag)
  }


}

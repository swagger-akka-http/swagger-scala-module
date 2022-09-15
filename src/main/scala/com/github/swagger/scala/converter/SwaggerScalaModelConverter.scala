package com.github.swagger.scala.converter

import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.databind.{JavaType, ObjectMapper}
import com.fasterxml.jackson.module.scala.introspect.{BeanIntrospector, PropertyDescriptor}
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, JsonScalaEnumeration}
import io.swagger.v3.core.converter._
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.{Json, PrimitiveType}
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.{ArraySchema, Schema => SchemaAnnotation}
import io.swagger.v3.oas.models.media.Schema
import org.slf4j.LoggerFactory

import java.lang.annotation.Annotation
import java.lang.reflect.ParameterizedType
import java.util
import scala.collection.JavaConverters._
import scala.collection.Seq
import scala.util.Try
import scala.util.control.NonFatal

class AnnotatedTypeForOption extends AnnotatedType

object SwaggerScalaModelConverter {
  val objectMapper: ObjectMapper = Json.mapper().registerModule(DefaultScalaModule)
  private var requiredBasedOnAnnotation = true

  /** If you use swagger annotations to override what is automatically derived, then be aware that
    * [[io.swagger.v3.oas.annotations.media.Schema]] annotation has required = [[false]], by default. You are advised to set the required
    * flag on this annotation to the correct value. If you would prefer to have the Schema annotation required flag ignored and to rely on
    * the this module inferring the value (as ot would if you don't annotate the classes or fields), then set
    * [[SwaggerScalaModelConverter.setRequiredBasedOnAnnotation]] to [[true]] and the required property on the annotation will be ignored,
    * unless the field is an [[Option]].
    *
    * @param value
    *   true by default
    */
  def setRequiredBasedOnAnnotation(value: Boolean = true): Unit = {
    requiredBasedOnAnnotation = value
  }

  /** If you use swagger annotations to override what is automatically derived, then be aware that
    * [[io.swagger.v3.oas.annotations.media.Schema]] annotation has required = [[false]], by default. You are advised to set the required
    * flag on this annotation to the correct value. If you would prefer to have the Schema annotation required flag ignored and to rely on
    * the this module inferring the value (as ot would if you don't annotate the classes or fields), then set
    * [[SwaggerScalaModelConverter.setRequiredBasedOnAnnotation]] to [[true]] and the required property on the annotation will be ignored,
    * unless the field is an [[Option]].
    *
    * @return
    *   value: true by default
    */
  def isRequiredBasedOnAnnotation: Boolean = requiredBasedOnAnnotation
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
        val baseType =
          if (
            SwaggerScalaModelConverter.isRequiredBasedOnAnnotation
            && annotatedOverrides.headOption.getOrElse(false)
          ) new AnnotatedType()
          else new AnnotatedTypeForOption()
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

  private def caseClassSchema(
      cls: Class[_],
      `type`: AnnotatedType,
      context: ModelConverterContext,
      chain: util.Iterator[ModelConverter]
  ): Option[Schema[_]] = {
    if (chain.hasNext) {
      Option(chain.next().resolve(`type`, context, chain)).map { schema =>
        val introspector = BeanIntrospector(cls)
        filterUnwantedProperties(schema, introspector.properties)
        val erasedProperties = ErasureHelper.erasedOptionalPrimitives(cls)
        val schemaProperties = nullSafeMap(schema.getProperties)
        introspector.properties.foreach { property =>
          val propertyName = property.name
          val (propertyClass, propertyAnnotations) = getPropertyClassAndAnnotations(property)
          val isOptional = isOption(propertyClass)
          val schemaOverride = propertyAnnotations.collectFirst { case s: SchemaAnnotation => s }
          val schemaOverrideClass = schemaOverride.flatMap { s =>
            // this form is needed by the Scala 2.11 compiler
            val classOption: Option[Class[_]] = if (s.implementation() == VoidClass) None else Option(s.implementation())
            classOption
          }
          val maybeDefault = property.param.flatMap(_.defaultValue)
          val schemaDefaultValue = schemaOverride.flatMap { s =>
            Option(s.defaultValue()).flatMap(str => if (str.isEmpty) None else Some(str))
          }
          val hasDefaultValue = schemaDefaultValue.nonEmpty || maybeDefault.nonEmpty

          if (schemaDefaultValue.isEmpty) {
            // default values set in annotation leads to default values set in Scala constructor being ignored
            maybeDefault.foreach { default =>
              schemaProperties.get(propertyName).foreach { property =>
                val defaultValue = default()
                defaultValue match {
                  case None =>
                  case _ => {
                    defaultValue match {
                      case Some(wrappedValue) => property.setDefault(wrappedValue)
                      case None => //no default
                      case seq: Seq[_] => property.setDefault(seq.asJava)
                      case set: Set[_] => property.setDefault(set.asJava)
                      case dv => property.setDefault(dv)
                    }
                  }
                }
              }
            }
          }

          if (schemaProperties.nonEmpty && schemaOverrideClass.isEmpty) {
            erasedProperties.get(propertyName).foreach { erasedType =>
              schemaProperties.get(propertyName).foreach { property =>
                Option(PrimitiveType.fromType(erasedType)).foreach { primitiveType =>
                  if (isOptional) {
                    schema.addProperty(propertyName, correctSchema(property, primitiveType))
                  }
                  if (isIterable(propertyClass) && !isMap(propertyClass)) {
                    schema.addProperty(propertyName, updateTypeOnItemsSchema(primitiveType, property))
                  }
                }
              }
            }
          }
          propertyAnnotations match {
            case Seq() => {
              val requiredFlag = !isOptional && !hasDefaultValue
              if (!requiredFlag && schema.getRequired != null && schema.getRequired.contains(propertyName)) {
                schema.getRequired.remove(propertyName)
              } else if (requiredFlag) {
                addRequiredItem(schema, propertyName)
              }
            }
            case annotations => {
              val annotationRequired = getRequiredSettings(annotations).headOption.getOrElse(false)
              if (SwaggerScalaModelConverter.isRequiredBasedOnAnnotation) {
                setRequiredBasedOnAnnotation(schema, propertyName, annotationRequired)
              } else {
                setRequiredBasedOnType(schema, propertyName, isOptional, hasDefaultValue, annotationRequired)
              }
            }
          }

        }
        schema
      }
    } else {
      None
    }
  }

  private def filterUnwantedProperties(schema: Schema[_], propertiesToKeep: Seq[PropertyDescriptor]): Unit = {
    val propNamesSet = propertiesToKeep.map(getAnnotatedPropertyName).toSet
    val originalProps = nullSafeMap(schema.getProperties)
    val newProps = originalProps.filter { case (key, value) =>
      propNamesSet.contains(key)
    }
    if (originalProps.size > newProps.size) {
      schema.setProperties(newProps.asJava)
    }
  }

  private def getAnnotatedPropertyName(property: PropertyDescriptor): String = {
    val propertyAnnotations = getPropertyAnnotations(property)
    propertyAnnotations.collectFirst { case s: SchemaAnnotation => s } match {
      case Some(ann) if ann.name().nonEmpty => ann.name()
      case _ => property.name
    }
  }

  private def setRequiredBasedOnAnnotation(
      schema: Schema[_],
      propertyName: String,
      annotationSetting: Boolean
  ): Unit = {
    if (annotationSetting) addRequiredItem(schema, propertyName)
  }

  private def setRequiredBasedOnType(
      schema: Schema[_],
      propertyName: String,
      isOptional: Boolean,
      hasDefaultValue: Boolean,
      annotationSetting: Boolean
  ): Unit = {
    val required = if (isOptional) {
      annotationSetting
    } else {
      !hasDefaultValue
    }
    if (required) addRequiredItem(schema, propertyName)
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
    case _ => getRequiredSettings(nullSafeSeq(annotatedType.getCtxAnnotations))
  }

  private def getRequiredSettings(annotations: Seq[Annotation]): Seq[Boolean] = {
    annotations.collect {
      case p: Parameter => p.required()
      case s: SchemaAnnotation => s.required()
      case a: ArraySchema => a.arraySchema().required()
    }
  }

  private def hasTypeOverride(ann: SchemaAnnotation): Boolean = {
    !(ann.implementation() == VoidClass && ann.`type`() == "")
  }

  private def matchScalaPrimitives(`type`: AnnotatedType, nullableClass: Class[_]): Option[Schema[_]] = {
    val annotations = Option(`type`.getCtxAnnotations).map(_.toSeq).getOrElse(Seq.empty)
    annotations.collectFirst { case ann: SchemaAnnotation if hasTypeOverride(ann) => ann } match {
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
      Try(Class.forName(cname.substring(0, cname.length - 1), true, Thread.currentThread.getContextClassLoader)).getOrElse(clazz)
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
    baseType
      .`type`(underlyingJavaType(`type`, javaType))
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
      val reqSettings = getRequiredSettings(annotatedType)
      val required = reqSettings.headOption.getOrElse(true)
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
          val onlyAnnotations = if (index > annotations.size) {
            Seq.empty[Annotation]
          }
          else {
            annotations(index).toIndexedSeq
          }
          (onlyType, onlyAnnotations)
        }
      case _ =>
        property.field match {
          case Some(field) => (field.getType, field.getAnnotations.toSeq)
          case _ =>
            property.setter match {
              case Some(setter) if setter.getParameterCount == 1 => {
                (setter.getParameterTypes()(0), setter.getAnnotations.toSeq)
              }
              case _ =>
                property.beanSetter match {
                  case Some(setter) if setter.getParameterCount == 1 => {
                    (setter.getParameterTypes()(0), setter.getAnnotations.toSeq)
                  }
                  case _ => (AnyClass, Seq.empty)
                }
            }
        }
    }
  }

  private def getPropertyAnnotations(property: PropertyDescriptor): Seq[Annotation] = {
    property.field match {
      case Some(field) => field.getAnnotations.toSeq
      case _ => property.setter match {
        case Some(setter) if setter.getParameterCount == 1 => setter.getAnnotations.toSeq
        case _ => property.beanSetter match {
          case Some(setter) if setter.getParameterCount == 1 => setter.getAnnotations.toSeq
          case _ => property.param match {
            case Some(constructorParameter) if 1 == 2 => {
              val types = constructorParameter.constructor.getParameterTypes
              val annotations = constructorParameter.constructor.getParameterAnnotations
              val index = constructorParameter.index
              if (index > types.size) {
                Seq.empty
              } else {
                if (index > annotations.size) {
                  Seq.empty[Annotation]
                }
                else {
                  annotations(index).toIndexedSeq
                }
              }
            }
          }
        }
      }
    }
  }

  private def isOption(cls: Class[_]): Boolean = cls == OptionClass
  private def isIterable(cls: Class[_]): Boolean = IterableClass.isAssignableFrom(cls)
  private def isMap(cls: Class[_]): Boolean = MapClass.isAssignableFrom(cls)
  private def isCaseClass(cls: Class[_]): Boolean = ProductClass.isAssignableFrom(cls)

  private def nullSafeSeq[T](array: Array[T]): Seq[T] = Option(array) match {
    case None => List.empty[T]
    case Some(arr) => arr.toList
  }

  private def nullSafeMap[K, V](map: java.util.Map[K, V]): Map[K, V] = Option(map) match {
    case None => Map.empty[K, V]
    case Some(m) => m.asScala.toMap
  }
}

package com.github.swagger.scala.converter

import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.databind.{JavaType, ObjectMapper}
import com.fasterxml.jackson.module.scala.util.ClassW
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, JsonScalaEnumeration}
import io.swagger.v3.core.converter._
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.{Json, PrimitiveType}
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import io.swagger.v3.oas.annotations.media.{ArraySchema => ArraySchemaAnnotation, Schema => SchemaAnnotation}
import io.swagger.v3.oas.models.media.{ArraySchema, MapSchema, ObjectSchema, Schema}
import org.slf4j.LoggerFactory

import java.lang.annotation.Annotation
import java.lang.reflect.ParameterizedType
import java.util
import scala.collection.JavaConverters._
import scala.util.Try
import scala.util.control.NonFatal

class AnnotatedTypeForOption extends AnnotatedType

object SwaggerScalaModelConverter {
  private val objectMapper: ObjectMapper = Json.mapper().registerModule(DefaultScalaModule)
  // https://github.com/swagger-api/swagger-core/issues/5076
  // hardcode here to avoid having an explicit dependence on the new DEFAULT_SENTINEL field in swagger-annotations
  private val DEFAULT_SENTINEL = "##default"

  private var requiredBasedOnAnnotation = true
  private var requiredBasedOnDefaultValue = true

  /** If you use swagger annotations to override what is automatically derived, then be aware that
    * [[io.swagger.v3.oas.annotations.media.Schema]] annotation has required = false, by default. You are advised to set the required flag
    * on this annotation to the correct value. If you would prefer to have the Schema annotation required flag ignored and to rely on the
    * this module inferring the value (as it would if you don't annotate the classes or fields), then set
    * [[SwaggerScalaModelConverter.setRequiredBasedOnAnnotation]] to true and the required property on the annotation will be ignored,
    * unless the field is an [[Option]].
    *
    * @param value
    *   true by default
    * @since v2.7.4
    */
  def setRequiredBasedOnAnnotation(value: Boolean = true): Unit = {
    requiredBasedOnAnnotation = value
  }

  /** If you use swagger annotations to override what is automatically derived, then this flag will not be used. If you rely on this module
    * inferring the required flag (as it would if you don't annotate the classes or fields), then this flag will control how the required
    * flag is derived when a default value exists. If [[SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue]] is true and a property
    * has a default value, then it will not be required. However, if this flag is false, then a property will be required only if it's not
    * an [[Option]].
    *
    * @param value
    *   true by default
    * @since v2.7.6
    */
  def setRequiredBasedOnDefaultValue(value: Boolean = true): Unit = {
    requiredBasedOnDefaultValue = value
  }

  /** If you use swagger annotations to override what is automatically derived, then be aware that
    * [[io.swagger.v3.oas.annotations.media.Schema]] annotation has required = false, by default. You are advised to set the required flag
    * on this annotation to the correct value. If you would prefer to have the Schema annotation required flag ignored and to rely on the
    * this module inferring the value (as it would if you don't annotate the classes or fields), then set
    * [[SwaggerScalaModelConverter.setRequiredBasedOnAnnotation]] to true and the required property on the annotation will be ignored,
    * unless the field is an [[Option]].
    *
    * @return
    *   value value: true by default
    * @since v2.7.4
    */
  def isRequiredBasedOnAnnotation: Boolean = requiredBasedOnAnnotation

  /** If you use swagger annotations to override what is automatically derived, then this flag will not be used. If you rely on this module
    * inferring the required flag (as it would if you don't annotate the classes or fields), then this flag will control how the required
    * flag is derived when a default value exists. If [[SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue]] is true and a property
    * has a default value, then it will not be required. However, if this flag is false, then a property will be required only if it's not
    * an [[Option]].
    *
    * @return
    *   value: true by default
    * @since v2.7.6
    */
  def isRequiredBasedOnDefaultValue: Boolean = requiredBasedOnDefaultValue

  /** What this module works out about a model class by reflection - its properties, their types and their annotations - is cached, because
    * none of it can change while the application is running, and introspecting a class is by far the most expensive thing this module does.
    * This sets how many classes the cache holds.
    *
    * @param value
    *   1000 by default. Zero introspects a class on every resolve, as this module did before v2.16.0.
    * @since v2.16.0
    */
  def setIntrospectionCacheSize(value: Int): Unit = ClassIntrospection.setMaxEntries(value)

  /** @return
    *   how many classes the introspection cache holds
    * @since v2.16.0
    */
  def getIntrospectionCacheSize: Int = ClassIntrospection.getMaxEntries

  /** Empties the introspection cache. Only needed if the classes it holds have to be released, e.g. when an application is redeployed
    * without its classloader being discarded.
    *
    * @since v2.16.0
    */
  def clearIntrospectionCache(): Unit = ClassIntrospection.clear()

  /** @param annotatedType
    * @return
    *   collection flags based on any Swagger annotations for this type
    */
  def getRequiredSettings(annotatedType: AnnotatedType): Seq[Boolean] = annotatedType match {
    case _: AnnotatedTypeForOption => Seq.empty
    case _ => getRequiredSettings(nullSafeSeq(annotatedType.getCtxAnnotations))
  }

  private def getRequiredSettings(annotations: Seq[Annotation]): Seq[Boolean] = {
    val flags = annotations.collect {
      case p: Parameter => if (p.required()) RequiredMode.REQUIRED else RequiredMode.NOT_REQUIRED
      case s: SchemaAnnotation => {
        if (s.requiredMode() == RequiredMode.AUTO) {
          if (s.required()) {
            RequiredMode.REQUIRED
          } else if (isRequiredBasedOnAnnotation) {
            RequiredMode.NOT_REQUIRED
          } else {
            RequiredMode.AUTO
          }
        } else {
          s.requiredMode()
        }
      }
      case a: ArraySchemaAnnotation => {
        if (a.arraySchema().requiredMode() == RequiredMode.AUTO) {
          if (a.arraySchema().required()) {
            RequiredMode.REQUIRED
          } else if (isRequiredBasedOnAnnotation) {
            RequiredMode.NOT_REQUIRED
          } else {
            RequiredMode.AUTO
          }
        } else {
          a.arraySchema().requiredMode()
        }
      }
    }
    flags.flatMap {
      case RequiredMode.REQUIRED => Some(true)
      case RequiredMode.NOT_REQUIRED => Some(false)
      case _ => None
    }
  }

  private def nullSafeSeq[T](array: Array[T]): Seq[T] = Option(array) match {
    case None => Seq.empty[T]
    case Some(arr) => arr.toList
  }
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
  private val AnyClass = classOf[Any]

  override def resolve(`type`: AnnotatedType, context: ModelConverterContext, chain: util.Iterator[ModelConverter]): Schema[_] = {
    Option(`type`.getType) match {
      case Some(typeType) => {
        val javaType = _mapper.constructType(typeType)
        val subtypes = SubtypeHelper.findSubtypes(javaType.getRawClass)
        if (subtypes.isEmpty) {
          resolveWithoutSubtypes(javaType, `type`, context, chain)
        } else {
          val converters = chain.asScala.toSeq
          val schema = new ObjectSchema
          val subSchemas = subtypes.map { subtype =>
            val javaSubType = _mapper.constructType(subtype)
            resolveWithoutSubtypes(javaSubType, new AnnotatedType(subtype), context, converters.iterator.asJava)
          }
          schema.anyOf(subSchemas.asJava)
        }
      }
      case _ => None.orNull
    }
  }

  private def resolveWithoutSubtypes(
      javaType: JavaType,
      `type`: AnnotatedType,
      context: ModelConverterContext,
      chain: util.Iterator[ModelConverter]
  ): Schema[_] = {
    val cls = javaType.getRawClass
    matchScalaPrimitives(`type`, cls).getOrElse {
      // Unbox scala options
      val annotatedOverrides = SwaggerScalaModelConverter.getRequiredSettings(`type`)
      if (_isOptional(`type`, cls)) {
        val baseType =
          if (annotatedOverrides.headOption.getOrElse(false)) new AnnotatedType()
          else new AnnotatedTypeForOption()
        resolve(nextType(baseType, `type`, javaType), context, chain)
      } else if (!annotatedOverrides.headOption.getOrElse(true)) {
        resolve(nextType(new AnnotatedTypeForOption(), `type`, javaType), context, chain)
      } else if (isScalaClass(cls) && !isIterable(cls)) {
        scalaClassSchema(cls, `type`, context, chain).getOrElse(None.orNull)
      } else if (chain.hasNext) {
        val nextResolved = Option(chain.next().resolve(`type`, context, chain))
        nextResolved match {
          case Some(property) => {
            if (isIterable(cls)) {
              property.setRequired(null)
              property.setProperties(null)
              Option(`type`.getParent) match {
                case Some(_) => property.setName(null)
                case _ =>
              }
            }
            property
          }
          case None => None.orNull
        }
      } else {
        None.orNull
      }
    }
  }

  private def scalaClassSchema(
      cls: Class[_],
      `type`: AnnotatedType,
      context: ModelConverterContext,
      chain: util.Iterator[ModelConverter]
  ): Option[Schema[_]] = {
    if (chain.hasNext) {
      Option(chain.next().resolve(`type`, context, chain)).map { schema =>
        // worked out once per class - see ClassIntrospection
        val introspection = ClassIntrospection.of(cls)
        filterUnwantedProperties(schema, introspection)
        // not part of the introspection: a class can be registered after it has first been introspected
        val erasedProperties = ErasureHelper.erasedOptionalPrimitives(cls)
        val schemaProperties = nullSafeMap(schema.getProperties)
        introspection.properties.foreach { property =>
          val propertyName = property.name
          val propertyAnnotations = property.annotations
          val isOptional = property.isOption
          val maybeDefault = property.defaultValue
          val schemaDefaultValue = property.schemaDefaultValue
          val hasDefaultValue = property.hasDefaultValue

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
                      case None => // no default
                      case seq: Seq[_] => property.setDefault(seq.asJava)
                      case set: Set[_] => property.setDefault(set.asJava)
                      case dv => property.setDefault(dv)
                    }
                  }
                }
              }
            }
          }

          if (schemaProperties.nonEmpty && property.overrideClass.isEmpty) {
            erasedProperties.get(propertyName).foreach { erasedType =>
              schemaProperties.get(propertyName).foreach { propertySchema =>
                Option(PrimitiveType.fromType(erasedType)).foreach { primitiveType =>
                  if (isOptional) {
                    schema.addProperty(propertyName, tryCorrectSchema(propertySchema, primitiveType))
                  }
                  if (property.isIterable && !property.isMap) {
                    schema.addProperty(propertyName, updateTypeOnItemsSchema(primitiveType, propertySchema))
                  }
                }
              }
            }
          }
          propertyAnnotations match {
            case Seq() => {
              val requiredFlag = !isOptional && (!SwaggerScalaModelConverter.isRequiredBasedOnDefaultValue || !hasDefaultValue)
              if (!requiredFlag && Option(schema.getRequired).isDefined && schema.getRequired.contains(propertyName)) {
                val requiredFields = new util.ArrayList[String](schema.getRequired)
                requiredFields.remove(propertyName)
                schema.setRequired(requiredFields)
              } else if (requiredFlag && schema.getEnum == null) {
                addRequiredItem(schema, propertyName)
              }
            }
            case annotations => {
              val annotationRequired = SwaggerScalaModelConverter.getRequiredSettings(annotations).headOption
              setRequiredBasedOnType(schema, propertyName, isOptional, hasDefaultValue, annotationRequired)
            }
          }

        }
        schema
      }
    } else {
      None
    }
  }

  private def filterUnwantedProperties(schema: Schema[_], introspection: ClassIntrospection): Unit = {
    val propNamesSet = introspection.annotatedNames
    val originalProps = nullSafeMap(schema.getProperties)
    val newProps = originalProps.filter { case (key, _) =>
      propNamesSet.contains(key)
    }
    if (originalProps.size > newProps.size) {
      schema.setProperties(new util.LinkedHashMap(newProps.asJava))
    }
  }

  private def setRequiredBasedOnType(
      schema: Schema[_],
      propertyName: String,
      isOptional: Boolean,
      hasDefaultValue: Boolean,
      annotationSetting: Option[Boolean]
  ): Unit = {
    val required = annotationSetting match {
      case Some(req) => req
      case _ => {
        if (isOptional) {
          false
        } else if (SwaggerScalaModelConverter.isRequiredBasedOnDefaultValue) {
          !hasDefaultValue
        } else {
          true
        }
      }
    }
    if (required) addRequiredItem(schema, propertyName)
  }

  private def updateTypeOnItemsSchema(primitiveType: PrimitiveType, propertySchema: Schema[_]): Schema[_] = {
    val updatedSchema = tryCorrectSchema(propertySchema.getItems, primitiveType)
    propertySchema.setItems(updatedSchema)
    propertySchema
  }

  private[converter] def tryCorrectSchema(itemSchema: Schema[_], primitiveType: PrimitiveType): Schema[_] = {
    itemSchema match {
      case ms: MapSchema => ms
      case as: ArraySchema => {
        val correctedSchema = tryCorrectSchema(as.getItems, primitiveType)
        as.setItems(correctedSchema)
        as
      }
      case _ => {
        Try {
          val primitiveProperty = primitiveType.createProperty()
          val propAsString = objectMapper.writeValueAsString(itemSchema)
          val correctedSchema = objectMapper.readValue(propAsString, primitiveProperty.getClass)
          correctedSchema.setType(primitiveProperty.getType)
          Option(itemSchema.getFormat) match {
            case Some(_) =>
            case _ => correctedSchema.setFormat(primitiveProperty.getFormat)
          }
          correctedSchema
        }.toOption.getOrElse(itemSchema)
      }
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
      val reqSettings = SwaggerScalaModelConverter.getRequiredSettings(annotatedType)
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

  private def isOption(cls: Class[_]): Boolean = cls == OptionClass
  private def isIterable(cls: Class[_]): Boolean = IterableClass.isAssignableFrom(cls)
  private def isMap(cls: Class[_]): Boolean = MapClass.isAssignableFrom(cls)

  private def isScalaClass(cls: Class[_]): Boolean = {
    val classW = ClassW(cls)
    classW.extendsScalaClass(true) || (!cls.getName.startsWith("scala.") && classW.hasSignature)
  }

  private def nullSafeMap[K, V](map: java.util.Map[K, V]): Map[K, V] = Option(map) match {
    case None => Map.empty[K, V]
    case Some(m) => m.asScala.toMap
  }
}

package com.github.swagger.scala.converter

import java.lang.reflect.ParameterizedType
import java.util.Iterator

import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.`type`.ReferenceType
import com.fasterxml.jackson.module.scala.{DefaultScalaModule, JsonScalaEnumeration}
import io.swagger.v3.core.converter._
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.core.util.RefUtils.constructRef
import io.swagger.v3.core.util.{AnnotationsUtils, Json, PrimitiveType, ReflectionUtils}
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.{ArraySchema => ArraySchemaAnnotation, Schema => SchemaAnnotation}
import io.swagger.v3.oas.models.media.{ArraySchema, MapSchema, Schema, XML}
import javax.xml.bind.annotation.XmlElement
import org.apache.commons.lang3.StringUtils

class AnnotatedTypeForOption extends AnnotatedType

object SwaggerScalaModelConverter {
  Json.mapper().registerModule(new DefaultScalaModule())
}

class SwaggerScalaModelConverter extends ModelResolver(Json.mapper()) {
  SwaggerScalaModelConverter

  override def resolve(`type`: AnnotatedType, context: ModelConverterContext, chain: Iterator[ModelConverter]): Schema[_] = {
    val javaType = _mapper.constructType(`type`.getType)
    val cls = javaType.getRawClass

    matchScalaPrimitives(`type`, cls).getOrElse {
      // Unbox scala options
      val annotatedOverrides = getRequiredSettings(`type`)
      if (_isOptional(`type`, cls)) {
        val baseType = if (annotatedOverrides.headOption.getOrElse(false)) new AnnotatedType() else new AnnotatedTypeForOption()
        resolve(nextType(baseType, `type`, javaType), context, chain)
      } else if (_isScalaCollection(`type`, cls)) {
        val baseType = if (annotatedOverrides.headOption.getOrElse(false)) new AnnotatedType() else new AnnotatedTypeForOption()
        resolve(nextType(baseType, `type`, javaType), context, chain)
      } else if (!annotatedOverrides.headOption.getOrElse(true)) {
        resolve(nextType(new AnnotatedTypeForOption(), `type`, javaType), context, chain)
      } else if (chain.hasNext) {
        val nextResolved = Option(chain.next().resolve(`type`, context, chain))
        nextResolved match {
          case Some(property) => {
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

  private def getRequiredSettings(`type`: AnnotatedType): Seq[Boolean] = `type` match {
    case _: AnnotatedTypeForOption => Seq.empty
    case _ => {
      nullSafeList(`type`.getCtxAnnotations).collect {
        case p: Parameter => p.required()
        case s: SchemaAnnotation => s.required()
      }
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
            val cls = args(0).asInstanceOf[Class[Enumeration]]
            getEnumerationInstance(cls).map { enum =>
              val sp: Schema[String] = PrimitiveType.STRING.createProperty().asInstanceOf[Schema[String]]
              setRequired(`type`)
              enum.values.iterator.foreach { v =>
                sp.addEnumItemObject(v.toString)
              }
              sp
            }
          }
          case _ => {
            Option(nullableClass).flatMap { cls =>
              if (cls == classOf[BigDecimal]) {
                val dp = PrimitiveType.DECIMAL.createProperty()
                setRequired(`type`)
                Some(dp)
              } else if (cls == classOf[BigInt]) {
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

  private def _isOptional(annotatedType: AnnotatedType, cls: Class[_]): Boolean = {
    annotatedType.getType match {
      case _: ReferenceType if isOption(cls) => true
      case _ => false
    }
  }

  private def _isScalaCollection(annotatedType: AnnotatedType, cls: Class[_]): Boolean = {
    annotatedType.getType match {
      case _: ReferenceType if isScalaCollection(cls) => true
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
      interface == classOf[scala.collection.Set[_]]
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

  private def getEnumerationInstance(cls: Class[_]): Option[Enumeration] = {
    if (cls.getFields.map(_.getName).contains("MODULE$")) {
      val javaUniverse = scala.reflect.runtime.universe
      val m = javaUniverse.runtimeMirror(Thread.currentThread().getContextClassLoader)
      val moduleMirror = m.reflectModule(m.staticModule(cls.getName))
      moduleMirror.instance match
      {
        case enumInstance: Enumeration => Some(enumInstance)
        case _ => None
      }
    }
    else None
  }

  private def isOption(cls: Class[_]): Boolean = cls == classOf[scala.Option[_]]

  private def isScalaCollection(cls: Class[_]): Boolean = {
    cls == classOf[scala.collection.Iterable[_]]
  }

  private def nullSafeList[T](array: Array[T]): List[T] = Option(array) match {
    case None => List.empty[T]
    case Some(arr) => arr.toList
  }

  private def collectionType(annotatedType: AnnotatedType, `type`: JavaType) = {
    val resolvedSchemaAnnotation = Option(AnnotationsUtils.mergeSchemaAnnotations(annotatedType.getCtxAnnotations, `type`)).flatMap {
      case arraySchema: ArraySchemaAnnotation => Some(arraySchema.schema)
      case schema: SchemaAnnotation => Some(schema)
      case _ => None
    }
    val keyType = `type`.getKeyType
    val valueType = `type`.getContentType
    var pName: String = null
    if (valueType != null) {
      val valueTypeBeanDesc = _mapper.getSerializationConfig.introspect(valueType)
      pName = _typeName(valueType, valueTypeBeanDesc)
    }
    val schemaAnnotations = resolvedSchemaAnnotation.toSeq
    if (keyType != null && valueType != null) {
      if (ReflectionUtils.isSystemType(`type`) && !annotatedType.isSchemaProperty && !annotatedType.isResolveAsRef) {
        resolve(new AnnotatedType().`type`(valueType).jsonViewAnnotation(annotatedType.getJsonViewAnnotation))
        return null
      }
      var addPropertiesSchema = resolve(new AnnotatedType().`type`(valueType).schemaProperty(annotatedType.isSchemaProperty).ctxAnnotations(schemaAnnotations).skipSchemaName(true).resolveAsRef(annotatedType.isResolveAsRef).jsonViewAnnotation(annotatedType.getJsonViewAnnotation).propertyName(annotatedType.getPropertyName).parent(annotatedType.getParent))
      if (addPropertiesSchema != null) {
        if (StringUtils.isNotBlank(addPropertiesSchema.getName)) pName = addPropertiesSchema.getName
        if ("object" == addPropertiesSchema.getType && pName != null) { // create a reference for the items
          if (context.getDefinedModels.containsKey(pName)) addPropertiesSchema = new Schema[_]().$ref(constructRef(pName))
        }
        else if (addPropertiesSchema.get$ref != null) addPropertiesSchema = new Schema[_]().$ref(if (StringUtils.isNotEmpty(addPropertiesSchema.get$ref)) addPropertiesSchema.get$ref
        else addPropertiesSchema.getName)
      }
      val mapModel = new MapSchema().additionalProperties(addPropertiesSchema)
      mapModel.name(name)
      mapModel
    }
    else if (valueType != null) {
      if (ReflectionUtils.isSystemType(`type`) && !annotatedType.isSchemaProperty && !annotatedType.isResolveAsRef) {
        resolve(new AnnotatedType().`type`(valueType).jsonViewAnnotation(annotatedType.getJsonViewAnnotation))
        return null
      }
      var items = resolve(new AnnotatedType().`type`(valueType).schemaProperty(annotatedType.isSchemaProperty).ctxAnnotations(schemaAnnotations).skipSchemaName(true).resolveAsRef(annotatedType.isResolveAsRef).propertyName(annotatedType.getPropertyName).jsonViewAnnotation(annotatedType.getJsonViewAnnotation).parent(annotatedType.getParent))
      if (items == null) return null
      if (annotatedType.isSchemaProperty && annotatedType.getCtxAnnotations != null && annotatedType.getCtxAnnotations.length > 0) if (!("object" == items.getType)) for (annotation <- annotatedType.getCtxAnnotations) {
        if (annotation.isInstanceOf[XmlElement]) {
          val xmlElement = annotation.asInstanceOf[XmlElement]
          if (xmlElement != null && xmlElement.name != null && !("" == xmlElement.name) && !("##default" == xmlElement.name)) {
            val xml = if (items.getXml != null) items.getXml
            else new XML
            xml.setName(xmlElement.name)
            items.setXml(xml)
          }
        }
      }
      if (StringUtils.isNotBlank(items.getName)) pName = items.getName
      if ("object" == items.getType && pName != null) if (context.getDefinedModels.containsKey(pName)) items = new Schema[_]().$ref(constructRef(pName))
      else if (items.get$ref != null) items = new Schema[_]().$ref(if (StringUtils.isNotEmpty(items.get$ref)) items.get$ref
      else items.getName)
      val arrayModel = new ArraySchema().items(items)
      if (_isSetType(`type`.getRawClass)) arrayModel.setUniqueItems(true)
      arrayModel.name(name)
      arrayModel
    }
    else if (ReflectionUtils.isSystemType(`type`) && !annotatedType.isSchemaProperty && !annotatedType.isResolveAsRef) {
      null
    }
  }
}

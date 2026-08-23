package com.github.swagger.scala.converter

import com.fasterxml.jackson.module.scala.introspect.{BeanIntrospector, PropertyDescriptor}
import io.swagger.v3.oas.annotations.media.{ArraySchema => ArraySchemaAnnotation, Schema => SchemaAnnotation}

import com.fasterxml.jackson.databind.util.LRUMap

import java.lang.annotation.Annotation

/** What the converter needs to know about one property of a model class, worked out once for the class rather than on every resolve. */
private[converter] final class PropertyIntrospection(
    val name: String,
    val annotatedName: String,
    val propertyClass: Class[_],
    val annotations: Seq[Annotation],
    val isOption: Boolean,
    val isIterable: Boolean,
    val isMap: Boolean,
    val schemaOverrideClass: Option[Class[_]],
    val arraySchemaOverrideClass: Option[Class[_]],
    val schemaDefaultValue: Option[String],
    val defaultValue: Option[() => Any]
) {

  /** Whether the property has a default value, either in the Scala constructor or in a Schema annotation. */
  val hasDefaultValue: Boolean = schemaDefaultValue.nonEmpty || defaultValue.nonEmpty

  /** The class the schema of this property should be overridden with, if any. */
  val overrideClass: Option[Class[_]] = schemaOverrideClass.orElse(arraySchemaOverrideClass)
}

private[converter] final class ClassIntrospection(val properties: Seq[PropertyIntrospection]) {

  /** The names the properties of this class have in a generated schema. */
  val annotatedNames: Set[String] = properties.map(_.annotatedName).toSet
}

/** Caches what can be worked out about a model class by reflection.
  *
  * Introspecting a class is the most expensive thing this module does per resolve - most of it inside jackson-module-scala's
  * `BeanIntrospector` - and none of it can change while the application is running, so it is worked out once per class.
  *
  * Only facts that are fixed for a class are held here. Anything that depends on how the converter is configured, such as
  * [[SwaggerScalaModelConverter.setRequiredBasedOnAnnotation]], and anything that can be registered later, such as the erased element types
  * of [[ScalaModelRegistry]], is still worked out on each resolve.
  */
private[converter] object ClassIntrospection {
  private val VoidClass = classOf[Void]
  private val OptionClass = classOf[scala.Option[_]]
  private val IterableClass = classOf[scala.collection.Iterable[_]]
  private val MapClass = classOf[scala.collection.Map[_, _]]
  private val AnyClass = classOf[Any]
  // https://github.com/swagger-api/swagger-core/issues/5076
  // hardcode here to avoid having an explicit dependence on the new DEFAULT_SENTINEL field in swagger-annotations
  private val DefaultSentinel = "##default"
  private val DefaultMaxEntries = 1000

  // the same bounded cache jackson uses for its own class lookups, so that a long lived application cannot accumulate
  // introspections without limit, and a redeployed one does not hold on to the classes of the previous deployment for ever
  @volatile private var cache = new LRUMap[Class[_], ClassIntrospection](16, DefaultMaxEntries)
  @volatile private var maxEntries = DefaultMaxEntries

  def of(cls: Class[_]): ClassIntrospection = {
    val cached = cache.get(cls)
    if (cached ne null) {
      cached
    } else {
      val introspection = introspect(cls)
      if (maxEntries > 0) cache.putIfAbsent(cls, introspection)
      introspection
    }
  }

  def clear(): Unit = cache.clear()

  /** Sets how many classes the cache holds. Zero introspects a class on every resolve, which is what this module did before v2.16.0. */
  def setMaxEntries(value: Int): Unit = {
    maxEntries = value
    cache = new LRUMap[Class[_], ClassIntrospection](16.min(value.max(1)), value.max(1))
  }

  def getMaxEntries: Int = maxEntries

  private def introspect(cls: Class[_]): ClassIntrospection = {
    val properties = BeanIntrospector(cls).properties.map { property =>
      val annotations = getPropertyAnnotations(property)
      val schemaOverride = annotations.collectFirst { case s: SchemaAnnotation => s }
      val schemaOverrideClass = schemaOverride.flatMap { s =>
        // this form is needed by the Scala 2.11 compiler
        val classOption: Option[Class[_]] = if (s.implementation() == VoidClass) None else Option(s.implementation())
        classOption
      }
      val arraySchemaOverrideClass = if (schemaOverride.nonEmpty) {
        None
      } else {
        annotations.collectFirst { case as: ArraySchemaAnnotation => as }.flatMap { as =>
          val itemSchema = as.schema()
          val classOption: Option[Class[_]] = if (itemSchema == null || itemSchema.implementation() == VoidClass) {
            None
          } else {
            Option(itemSchema.implementation())
          }
          classOption
        }
      }
      val schemaDefaultValue = schemaOverride.flatMap { s =>
        Option(s.defaultValue()).flatMap { str =>
          if (str.isEmpty || str == DefaultSentinel) None else Some(str)
        }
      }
      val annotatedName = schemaOverride match {
        case Some(ann) if ann.name().nonEmpty => ann.name()
        case _ => property.name
      }
      val propertyClass = getPropertyClass(property)
      new PropertyIntrospection(
        name = property.name,
        annotatedName = annotatedName,
        propertyClass = propertyClass,
        annotations = annotations,
        isOption = propertyClass == OptionClass,
        isIterable = IterableClass.isAssignableFrom(propertyClass),
        isMap = MapClass.isAssignableFrom(propertyClass),
        schemaOverrideClass = schemaOverrideClass,
        arraySchemaOverrideClass = arraySchemaOverrideClass,
        schemaDefaultValue = schemaDefaultValue,
        defaultValue = property.param.flatMap(_.defaultValue)
      )
    }
    new ClassIntrospection(properties)
  }

  private def getPropertyClass(property: PropertyDescriptor): Class[_] = {
    property.param match {
      case Some(constructorParameter) =>
        val types = constructorParameter.constructor.getParameterTypes
        val index = constructorParameter.index
        if (index > types.size) {
          AnyClass
        } else {
          types(index)
        }
      case _ =>
        property.field match {
          case Some(field) => field.getType
          case _ =>
            property.setter match {
              case Some(setter) if setter.getParameterCount == 1 => {
                setter.getParameterTypes()(0)
              }
              case _ =>
                property.beanSetter match {
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
    val fieldAnnotations = property.field match {
      case Some(field) => field.getAnnotations.toSeq
      case _ => Seq.empty
    }
    val setterAnnotations = property.setter match {
      case Some(setter) => setter.getAnnotations.toSeq
      case _ => Seq.empty
    }
    val beanSetterAnnotations = property.beanSetter match {
      case Some(beanSetter) => beanSetter.getAnnotations.toSeq
      case _ => Seq.empty
    }
    val paramAnnotations = property.param match {
      case Some(constructorParameter) => {
        val types = constructorParameter.constructor.getParameterTypes
        val annotations = constructorParameter.constructor.getParameterAnnotations
        val index = constructorParameter.index
        if (index > types.size || index > annotations.size) {
          Seq.empty
        } else {
          annotations(index).toIndexedSeq
        }
      }
      case _ => Seq.empty
    }
    (paramAnnotations ++ fieldAnnotations ++ setterAnnotations ++ beanSetterAnnotations).distinct
  }
}

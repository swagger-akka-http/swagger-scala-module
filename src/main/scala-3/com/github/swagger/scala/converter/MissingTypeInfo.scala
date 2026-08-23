package com.github.swagger.scala.converter

import org.slf4j.LoggerFactory

import java.lang.reflect.{Modifier, ParameterizedType, Type}
import java.util.concurrent.ConcurrentHashMap
import scala.util.control.NonFatal

/** Logs a warning, once per class, about the classes that would need to be registered with [[ScalaModelRegistry]] for their schemas to be
  * accurate. The checks are only there to keep the warning off classes that would not benefit from being registered.
  */
private[converter] object MissingTypeInfo {
  private val logger = LoggerFactory.getLogger(MissingTypeInfo.getClass)
  private val warned = ConcurrentHashMap.newKeySet[String]()
  private val ObjectClass = classOf[Object]
  private val OptionClass = classOf[Option[?]]
  private val IterableClass = classOf[scala.collection.Iterable[?]]
  private val MapClass = classOf[scala.collection.Map[?, ?]]

  def warnOnce(cls: Class[?]): Unit = {
    if (warned.add(cls.getName)) {
      logger.warn(
        s"No Scala 3 type information is available for ${cls.getName} - its schema may be missing the element types of its Option and " +
          s"collection fields, as well as its sealed subtypes. Add `derives ScalaTypeInfo` to the class, or call " +
          s"ScalaModelRegistry.register[${cls.getSimpleName}] before generating schemas, to make this information available."
      )
    }
  }

  /** True if the class has a field whose generic signature has been erased to `Object`, i.e. one whose element type Java reflection cannot
    * recover, such as `Option[Int]` or `Seq[Long]`.
    */
  def hasErasedGenericFields(cls: Class[?]): Boolean = {
    try {
      cls.getDeclaredFields.exists { field =>
        val fieldType = field.getType
        val isWrapper =
          OptionClass.isAssignableFrom(fieldType) || IterableClass.isAssignableFrom(fieldType) || MapClass.isAssignableFrom(fieldType)
        isWrapper && isErasedToObject(field.getGenericType)
      }
    } catch {
      case NonFatal(_) => false
    }
  }

  /** True if the class could be a sealed Scala 3 type, i.e. one whose subtypes are worth registering. */
  def couldBeSealed(cls: Class[?]): Boolean = {
    (cls.isInterface || Modifier.isAbstract(cls.getModifiers)) && isScala3Class(cls)
  }

  private def isErasedToObject(genericType: Type): Boolean = genericType match {
    case parameterized: ParameterizedType =>
      parameterized.getActualTypeArguments.lastOption.exists {
        case ObjectClass => true
        case nested => isErasedToObject(nested)
      }
    case _ => false
  }

  /** True if the class was compiled by Scala 3, which is the case when it has a TASTy file next to its class file. Classes compiled by
    * other languages have nothing to register.
    */
  private def isScala3Class(cls: Class[?]): Boolean = {
    try {
      val binaryName = cls.getName.substring(cls.getName.lastIndexOf('.') + 1)
      val topLevelName = binaryName.takeWhile(_ != '$')
      cls.getResource(s"$topLevelName.tasty") != null
    } catch {
      case NonFatal(_) => false
    }
  }
}

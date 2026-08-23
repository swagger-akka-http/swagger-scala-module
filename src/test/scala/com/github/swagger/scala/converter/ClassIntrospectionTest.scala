package com.github.swagger.scala.converter

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import models._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

class ClassIntrospectionTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers {
  TestModelRegistration.register()

  private val converters = ModelConverters.getInstance()
  private val modelClass = classOf[ModelWOptionIntSchemaOverrideForRequired]

  override protected def afterEach(): Unit = {
    SwaggerScalaModelConverter.setIntrospectionCacheSize(1000)
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(true)
    SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue(true)
  }

  private def schemaOf(cls: Class[_]): String = Json.pretty(converters.readAll(cls))

  private def requiredFieldsOf(cls: Class[_]): Set[String] = {
    val schema = converters.readAll(cls).asScala(cls.getSimpleName)
    Option(schema.getRequired).map(_.asScala.toSet).getOrElse(Set.empty)
  }

  "ClassIntrospection" should "generate the same schema whether a class is cached or introspected every time" in {
    SwaggerScalaModelConverter.setIntrospectionCacheSize(0)
    val uncached = schemaOf(modelClass)
    SwaggerScalaModelConverter.setIntrospectionCacheSize(1000)
    schemaOf(modelClass) shouldEqual uncached // this one fills the cache
    schemaOf(modelClass) shouldEqual uncached // this one reads it
    SwaggerScalaModelConverter.clearIntrospectionCache()
    schemaOf(modelClass) shouldEqual uncached
  }

  it should "keep generating schemas for classes that do not fit in the cache" in {
    SwaggerScalaModelConverter.setIntrospectionCacheSize(1)
    val first = schemaOf(modelClass)
    schemaOf(classOf[ModelWSeqInt]) should not be empty
    schemaOf(classOf[ModelWOptionString]) should not be empty
    schemaOf(modelClass) shouldEqual first
  }

  it should "not cache what depends on how the converter is configured" in {
    SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue(true)
    val basedOnDefaultValue = requiredFieldsOf(modelClass)
    // the class has been introspected by now, so this only changes if the required settings are worked out per resolve
    SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue(false)
    val notBasedOnDefaultValue = requiredFieldsOf(modelClass)
    basedOnDefaultValue shouldEqual Set("annotatedOptionalInt", "requiredInt")
    notBasedOnDefaultValue shouldEqual Set("annotatedOptionalInt", "requiredInt", "requiredIntWithDefault")
  }

  it should "expose the cache size it was given" in {
    SwaggerScalaModelConverter.setIntrospectionCacheSize(42)
    SwaggerScalaModelConverter.getIntrospectionCacheSize shouldBe 42
  }
}

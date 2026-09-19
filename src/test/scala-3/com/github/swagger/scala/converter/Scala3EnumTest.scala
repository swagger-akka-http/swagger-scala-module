package com.github.swagger.scala.converter

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.{ArraySchema, Schema, StringSchema}
import models._
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._
import scala.reflect.ClassTag

class Scala3EnumTest extends AnyFlatSpec with Matchers with OptionValues {

  class PropertiesScope[A](implicit tt: ClassTag[A]) {
    val schemas = ModelConverters.getInstance().readAll(tt.runtimeClass).asScala.toMap
    val model = schemas.get(tt.runtimeClass.getSimpleName)
    model should be(defined)
    model.value.getProperties should not be (null)
  }

  it should "process a Scala 3 enum property as a string enum" in new PropertiesScope[Car] {
    val field = model.value.getProperties.get("color")
    field shouldBe a[StringSchema]
    nullSafeSeq(field.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    field.getRequired shouldBe null
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("color", "make")
    schemas.keySet shouldEqual Set("Car")
  }

  it should "process a Scala 3 enum nested in an object" in new PropertiesScope[CtxCar] {
    val field = model.value.getProperties.get("color")
    field shouldBe a[StringSchema]
    nullSafeSeq(field.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("color", "make")
  }

  it should "process a Set of Scala 3 enums" in new PropertiesScope[Colors] {
    val field = model.value.getProperties.get("set")
    field shouldBe an[ArraySchema]
    val arraySchema = field.asInstanceOf[ArraySchema]
    nullSafeSeq(arraySchema.getItems.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    arraySchema.getRequired shouldBe null
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("set")
  }

  it should "process an Option of a Scala 3 enum as not required" in new PropertiesScope[CarWOptionColor] {
    val field = model.value.getProperties.get("color")
    field shouldBe a[StringSchema]
    nullSafeSeq(field.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("make")
  }

  it should "process a Scala 3 enum with a default value as not required" in new PropertiesScope[CarWDefaultColor] {
    val field = model.value.getProperties.get("color")
    field shouldBe a[StringSchema]
    nullSafeSeq(field.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    field.getDefault shouldEqual "Red"
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("make")
  }

  it should "honour Schema annotation on a Scala 3 enum property" in new PropertiesScope[CarWAnnotatedColor] {
    val field = model.value.getProperties.get("color")
    field shouldBe a[StringSchema]
    nullSafeSeq(field.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    field.getDescription shouldEqual "paint colour"
    field.getDeprecated shouldBe true
    field.getExample shouldEqual "Green"
    field.getDefault shouldEqual "Blue"
    field.getReadOnly shouldBe true
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("color", "make")
  }

  it should "honour Schema type override on a Scala 3 enum property" in new PropertiesScope[CarWTypeOverriddenColor] {
    val field = model.value.getProperties.get("color")
    field shouldBe a[StringSchema]
    field.getEnum shouldBe null
    field.getDescription shouldEqual "free text colour"
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("color", "make")
  }

  it should "process a Scala 3 enum with parameterized cases using only the singleton cases" in new PropertiesScope[AdtCar] {
    val field = model.value.getProperties.get("color")
    field shouldBe a[StringSchema]
    nullSafeSeq(field.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("color", "make")
    schemas.keySet shouldEqual Set("AdtCar")
  }

  it should "process a Set of Scala 3 enums with parameterized cases" in new PropertiesScope[AdtColorSet] {
    val field = model.value.getProperties.get("set")
    field shouldBe an[ArraySchema]
    val arraySchema = field.asInstanceOf[ArraySchema]
    nullSafeSeq(arraySchema.getItems.getEnum) shouldEqual Seq("Red", "Green", "Blue")
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("set")
  }

  private def nullSafeSeq[T](list: java.util.List[T]): Seq[T] = Option(list) match {
    case None => Seq.empty[T]
    case Some(l) => l.asScala.toSeq
  }
}

package com.github.swagger.scala.converter

import io.swagger.v3.core.converter._
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.models.media.{ArraySchema, DateTimeSchema, IntegerSchema, StringSchema}
import models._

import scala.annotation.meta.field
import scala.collection.JavaConverters._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ScalaModelTest extends AnyFlatSpec with Matchers {
  it should "extract a scala enum" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[SModelWithEnum]).asScala
    val userSchema = schemas("SModelWithEnum")

    val orderSize = userSchema.getProperties().get("Order Size")
    orderSize should not be null
    // TODO fix tests
    //    orderSize shouldBe a [StringSchema]
    //
    //    val sp = orderSize.asInstanceOf[StringSchema]
    //    (sp.getEnum().asScala.toSet & Set("TALL", "GRANDE", "VENTI")) should have size 3
  }

  it should "extract a scala enum (jackson annotated)" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[SModelWithEnumJacksonAnnotated]).asScala
    val userSchema = schemas("SModelWithEnumJacksonAnnotated")

    val orderSize = userSchema.getProperties().get("orderSize")
    orderSize should not be null
    orderSize shouldBe a[StringSchema]
    val sp = orderSize.asInstanceOf[StringSchema]
    Option(sp.getEnum) shouldBe defined
    sp.getEnum().asScala.toSet shouldEqual Set("TALL", "GRANDE", "VENTI")
  }

  it should "extract a scala enum with custom value names (jackson annotated)" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[ModelWithTestEnum]).asScala
    val userSchema = schemas("ModelWithTestEnum")

    val enumModel = userSchema.getProperties().get("enumValue")
    enumModel should not be null
    enumModel shouldBe a[StringSchema]
    val sp = enumModel.asInstanceOf[StringSchema]
    Option(sp.getEnum) shouldBe defined
    sp.getEnum().asScala.toSet shouldEqual Set("a", "b")
  }

  it should "extract a java enum" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[ModelWithJavaEnum]).asScala
    val userSchema = schemas("ModelWithJavaEnum")

    val level = userSchema.getProperties().get("level")
    level shouldBe a[StringSchema]

    val sp = level.asInstanceOf[StringSchema]
    sp.getEnum().asScala.toSet shouldEqual Set("LOW", "MEDIUM", "HIGH")
  }

  it should "read a scala case class with properties" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[SimpleUser]).asScala
    val userSchema = schemas("SimpleUser")
    val id = userSchema.getProperties().get("id")
    id shouldBe a[IntegerSchema]

    val name = userSchema.getProperties().get("name")
    name shouldBe a[StringSchema]

    val date = userSchema.getProperties().get("date")
    date shouldBe a[DateTimeSchema]
//    date.getDescription should be ("the birthdate")
  }

  it should "read a model with vector property" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[ModelWithVector]).asScala
    val model = schemas("ModelWithVector")
    val friends = model.getProperties().get("friends")
    friends shouldBe a[ArraySchema]
  }

  it should "read a model with vector of ints" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[ModelWithIntVector]).asScala
    val model = schemas("ModelWithIntVector")
    val prop = model.getProperties().get("ints")
    prop shouldBe a [ArraySchema]
    prop shouldBe a[ArraySchema]
    prop.asInstanceOf[ArraySchema].getItems.getType should be("integer")
  }

  it should "read a model with vector of booleans" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[ModelWithBooleanVector]).asScala
    val model = schemas("ModelWithBooleanVector")
    val prop = model.getProperties().get("bools")
    prop shouldBe a [ArraySchema]
    prop.asInstanceOf[ArraySchema].getItems.getType should be("boolean")
  }
}

case class ModelWithVector(name: String, friends: Vector[String])

case class ModelWithIntVector(ints: Vector[Int])
case class ModelWithBooleanVector(bools: Vector[Boolean])

case class SimpleUser(id: Long, name: String, @(Parameter @field)(description = "the birthdate") date: java.util.Date)

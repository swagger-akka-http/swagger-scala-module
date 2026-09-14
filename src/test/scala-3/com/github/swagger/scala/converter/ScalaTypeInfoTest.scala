package com.github.swagger.scala.converter

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.{IntegerSchema, ObjectSchema, Schema}
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

object ScalaTypeInfoTest {
  case class Derived(optInt: Option[Int], longs: Seq[Long], byName: Map[String, Double], name: String) derives ScalaTypeInfo

  case class HoldsDerived(child: Reached, optLong: Option[Long]) derives ScalaTypeInfo
  case class Reached(optShort: Option[Short])

  sealed trait DerivedShape derives ScalaTypeInfo
  case class Rectangle(width: Double, sides: Option[Int]) extends DerivedShape
  case object Dot extends DerivedShape

  object Outer {
    case class Inner(optDouble: Option[Double]) derives ScalaTypeInfo
  }

  case class Generic[T](value: Option[T]) derives ScalaTypeInfo
}

class ScalaTypeInfoTest extends AnyFlatSpec with Matchers with OptionValues {
  import ScalaTypeInfoTest._

  "A derived ScalaTypeInfo" should "be found without the class being registered" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[Derived]) shouldBe Map(
      "optInt" -> classOf[Int],
      "longs" -> classOf[Long],
      "byName" -> classOf[Double]
    )
  }

  it should "be found for a class nested in an object" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[Outer.Inner]) shouldBe Map("optDouble" -> classOf[Double])
  }

  it should "provide the subtypes of a sealed trait" in {
    SubtypeHelper.findSubtypes(classOf[DerivedShape]) shouldBe Seq(Dot.getClass, classOf[Rectangle])
  }

  it should "also cover the types reachable from the derived class" in {
    ScalaModelRegistry.isRegistered(classOf[HoldsDerived]) shouldBe true
    ErasureHelper.erasedOptionalPrimitives(classOf[Reached]) shouldBe Map("optShort" -> classOf[Short])
  }

  it should "not be found for a class with type parameters" in {
    // the compiler asks for a ScalaTypeInfo of each type parameter, so the companion has no instance that can be read by class
    ScalaModelRegistry.isRegistered(classOf[Generic[String]]) shouldBe false
  }

  it should "be found again after the registry is cleared" in {
    try {
      ScalaModelRegistry.clear()
      ScalaModelRegistry.isRegistered(classOf[Derived]) shouldBe true
    } finally {
      TestModelRegistration.register() // the registry is global, so put back what the other suites registered
    }
  }

  it should "generate accurate schemas" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[Derived]).asScala.toMap
    val properties = schemas("Derived").getProperties.asScala
    properties("optInt") shouldBe an[IntegerSchema]
    properties("longs").getItems shouldBe an[IntegerSchema]
  }

  it should "generate an anyOf schema for a sealed trait" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[DerivedShape]).asScala.toMap
    val rectangle = schemas.get("Rectangle").value
    rectangle.getProperties.asScala("sides") shouldBe an[IntegerSchema]
  }
}

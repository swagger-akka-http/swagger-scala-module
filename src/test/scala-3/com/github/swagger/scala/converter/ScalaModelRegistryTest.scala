package com.github.swagger.scala.converter

import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.media.{IntegerSchema, ObjectSchema}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

object ScalaModelRegistryTest {
  case class Registered(optInt: Option[Int], longs: Seq[Long], byName: Map[String, Double], name: String)
  case class Unregistered(optInt: Option[Int])
  case class TupleRegistered(optLong: Option[Long])
  case class HoldsRegistered(child: Registered, others: Seq[Registered])

  sealed trait RegisteredShape
  case class Circle(radius: Double) extends RegisteredShape
  case object Square extends RegisteredShape

  sealed trait UnregisteredShape
  case class Triangle(base: Double) extends UnregisteredShape

  class LongList extends Seq[Long] {
    def apply(i: Int): Long = 0L
    def length: Int = 0
    def iterator: Iterator[Long] = Iterator.empty
  }
  case class HoldsLongList(values: LongList, optLong: Option[Long])
}

class ScalaModelRegistryTest extends AnyFlatSpec with Matchers {
  import ScalaModelRegistryTest._

  ScalaModelRegistry.register[HoldsRegistered]
  ScalaModelRegistry.register[RegisteredShape]
  ScalaModelRegistry.registerAll[TupleRegistered *: EmptyTuple]

  "ScalaModelRegistry" should "record the erased element types of a registered class" in {
    ScalaModelRegistry.isRegistered(classOf[Registered]) shouldBe true
    ErasureHelper.erasedOptionalPrimitives(classOf[Registered]) shouldBe Map(
      "optInt" -> classOf[Int],
      "longs" -> classOf[Long],
      "byName" -> classOf[Double]
    )
  }

  it should "register the types reachable from a registered class" in {
    ScalaModelRegistry.isRegistered(classOf[HoldsRegistered]) shouldBe true
    ScalaModelRegistry.isRegistered(classOf[Registered]) shouldBe true
  }

  it should "register every type of a tuple" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[TupleRegistered]) shouldBe Map("optLong" -> classOf[Long])
  }

  it should "record the subtypes of a registered sealed trait" in {
    SubtypeHelper.findSubtypes(classOf[RegisteredShape]) shouldBe Seq(classOf[Circle], Square.getClass)
  }

  it should "record the element type a class fixes for itself" in {
    ScalaModelRegistry.register[HoldsLongList]
    ErasureHelper.erasedOptionalPrimitives(classOf[HoldsLongList]) shouldBe Map("values" -> classOf[Long], "optLong" -> classOf[Long])
  }

  it should "have no type information for an unregistered class" in {
    ScalaModelRegistry.isRegistered(classOf[Unregistered]) shouldBe false
    ErasureHelper.erasedOptionalPrimitives(classOf[Unregistered]) shouldBe empty
    SubtypeHelper.findSubtypes(classOf[UnregisteredShape]) shouldBe empty
  }

  it should "generate accurate schemas for registered classes" in {
    val schemas = ModelConverters.getInstance().readAll(classOf[Registered]).asScala.toMap
    val properties = schemas("Registered").getProperties.asScala
    properties("optInt") shouldBe an[IntegerSchema]
    properties("longs").getItems shouldBe an[IntegerSchema]
  }

  "MissingTypeInfo" should "warn about a class whose element types have been erased" in {
    MissingTypeInfo.hasErasedGenericFields(classOf[Unregistered]) shouldBe true
    MissingTypeInfo.hasErasedGenericFields(classOf[Registered]) shouldBe true
  }

  it should "not warn about a class that has nothing to register" in {
    MissingTypeInfo.hasErasedGenericFields(classOf[Circle]) shouldBe false
    MissingTypeInfo.hasErasedGenericFields(classOf[String]) shouldBe false
    MissingTypeInfo.couldBeSealed(classOf[Circle]) shouldBe false
    MissingTypeInfo.couldBeSealed(classOf[java.util.List[String]]) shouldBe false
  }

  it should "warn about a class that could be a sealed Scala 3 type" in {
    MissingTypeInfo.couldBeSealed(classOf[UnregisteredShape]) shouldBe true
  }
}

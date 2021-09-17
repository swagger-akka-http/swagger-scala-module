package com.github.swagger.scala.converter

import java.util

import io.swagger.v3.core.converter._
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media._
import models._
import org.scalatest.OptionValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

class ModelPropertyParserTest extends AnyFlatSpec with Matchers with OptionValues {
  it should "verify swagger-core bug 814" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[CoreBug814])
    val model = schemas.get("CoreBug814")
    model should not be (null)
    model.getProperties should not be (null)
    val isFoo = model.getProperties().get("isFoo")
    isFoo should not be (null)
    isFoo shouldBe a[BooleanSchema]
  }

  it should "process Option[String] as string" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionString]).asScala.toMap
    val model = schemas.get("ModelWOptionString")
    model should be(defined)
    model.value.getProperties should not be(null)
    val stringOpt = model.value.getProperties().get("stringOpt")
    stringOpt should not be (null)
    stringOpt.isInstanceOf[StringSchema] should be(true)
    nullSafeList(stringOpt.getRequired) shouldBe empty
    val stringWithDataType = model.value.getProperties().get("stringWithDataTypeOpt")
    stringWithDataType should not be (null)
    stringWithDataType shouldBe a [StringSchema]
    nullSafeList(stringWithDataType.getRequired) shouldBe empty
  }

  it should "process Option[Model] as Model" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionModel]).asScala.toMap
    val model = schemas.get("ModelWOptionModel")
    model should be (defined)
    model.value.getProperties should not be (null)
    val modelOpt = model.value.getProperties().get("modelOpt")
    modelOpt should not be (null)
    modelOpt.get$ref() shouldEqual "#/components/schemas/ModelWOptionString"
  }

  it should "process Model with Scala BigDecimal as Number" in {
    case class TestModelWithBigDecimal(field: BigDecimal)

    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[TestModelWithBigDecimal]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigDecimal")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties().get("field")
    field shouldBe a [NumberSchema]
    nullSafeList(model.value.getRequired) should not be empty
  }

  it should "process Model with Scala BigInt as Number" in {
    case class TestModelWithBigInt(field: BigInt)

    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[TestModelWithBigInt]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigInt")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties().get("field")
    field shouldBe a [IntegerSchema]
    nullSafeList(model.value.getRequired) should not be empty
  }

  it should "process Model with Scala Option BigDecimal" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBigDecimal]).asScala.toMap
    val model = schemas.get("ModelWOptionBigDecimal")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBigDecimal = model.value.getProperties().get("optBigDecimal")
    optBigDecimal should not be (null)
    optBigDecimal shouldBe a [NumberSchema]
    nullSafeList(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option BigInt" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBigInt]).asScala.toMap
    val model = schemas.get("ModelWOptionBigInt")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBigInt = model.value.getProperties().get("optBigInt")
    optBigInt should not be (null)
    optBigInt shouldBe a [IntegerSchema]
    nullSafeList(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Int" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionInt]).asScala.toMap
    val model = schemas.get("ModelWOptionInt")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    optInt shouldBe a [Schema[_]]
    nullSafeList(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Int with Schema Override" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionIntSchemaOverride]).asScala.toMap
    val model = schemas.get("ModelWOptionIntSchemaOverride")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    optInt shouldBe a [IntegerSchema]
    nullSafeList(model.value.getRequired) shouldBe empty
  }
  
  it should "process Model with Scala Option Boolean" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBoolean]).asScala.toMap
    val model = schemas.get("ModelWOptionBoolean")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBoolean = model.value.getProperties().get("optBoolean")
    optBoolean should not be (null)
    optBoolean shouldBe a [Schema[_]]
    nullSafeList(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Boolean with Schema Override" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBooleanSchemaOverride]).asScala.toMap
    val model = schemas.get("ModelWOptionBooleanSchemaOverride")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBoolean = model.value.getProperties().get("optBoolean")
    optBoolean should not be (null)
    optBoolean shouldBe a [BooleanSchema]
    nullSafeList(model.value.getRequired) shouldBe empty
  }

  it should "process all properties as required barring Option[_] or if overridden in annotation" in {
    val schemas = ModelConverters
      .getInstance()
      .readAll(classOf[ModelWithOptionAndNonOption])
      .asScala

    val model = schemas("ModelWithOptionAndNonOption")
    model should not be (null)
    model.getProperties() should not be (null)

    val optional = model.getProperties().get("optional")
    optional should not be (null)

    val required = model.getProperties().get("required")
    required should not be (null)

    val forcedRequired = model.getProperties().get("forcedRequired")
    forcedRequired should not be (null)

    val forcedOptional = model.getProperties().get("forcedOptional")
    forcedOptional should not be (null)

    val requiredItems = nullSafeList(model.getRequired)
    requiredItems shouldBe List("forcedRequired", "required")
  }

  it should "process all properties as required barring Option[_] or if overridden in annotation (Schema annotation)" in {
    val schemas = ModelConverters
      .getInstance()
      .readAll(classOf[ModelWithOptionAndNonOption2])
      .asScala

    val model = schemas("ModelWithOptionAndNonOption2")
    model should not be (null)
    model.getProperties() should not be (null)

    val optional = model.getProperties().get("optional")
    optional should not be (null)

    val required = model.getProperties().get("required")
    required should not be (null)

    val forcedRequired = model.getProperties().get("forcedRequired")
    forcedRequired should not be (null)

    val forcedOptional = model.getProperties().get("forcedOptional")
    forcedOptional should not be (null)

    val requiredItems = nullSafeList(model.getRequired)
    requiredItems shouldBe List("forcedRequired", "required")
  }

  it should "handle null properties from converters later in the chain" in {
    object CustomConverter extends ModelConverter {
      override def resolve(`type`: AnnotatedType, context: ModelConverterContext, chain: util.Iterator[ModelConverter]): Schema[_] = {
        if (chain.hasNext) chain.next().resolve(`type`, context, chain) else null
      }
    }

    val converter = new ModelConverters()
    converter.addConverter(CustomConverter)
    converter.addConverter(new SwaggerScalaModelConverter)
    converter.readAll(classOf[Option[Int]])
  }

  it should "process Model with Scala BigDecimal with annotation" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWBigDecimalAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWBigDecimalAnnotated")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a [StringSchema]
    nullSafeList(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process Model with Scala BigInt with annotation" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWBigIntAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWBigIntAnnotated")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a [StringSchema]
    nullSafeList(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process Model with Scala Enum with annotation" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWEnumAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWEnumAnnotated")
    model should be(defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a[StringSchema]
    val stringSchema = field.asInstanceOf[StringSchema]
    stringSchema.getDescription shouldEqual "enum value"
    nullSafeList(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process ListReply Model" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ListReply[String]]).asScala.toMap
    val model = findModel(schemas, "ListReply")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("items")
    stringsField shouldBe a [ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a [ObjectSchema] //probably type erasure - ideally this would eval as StringSchema
    //next line used to fail (https://github.com/swagger-akka-http/swagger-akka-http/issues/171)
    Json.mapper().writeValueAsString(model.value)
  }

  it should "process Model with Scala Seq" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWSeqString]).asScala.toMap
    val model = findModel(schemas, "ModelWSeqString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a [StringSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    nullSafeList(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Set" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWSetString]).asScala.toMap
    val model = findModel(schemas, "ModelWSetString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe true
    arraySchema.getItems shouldBe a [StringSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    nullSafeList(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Java List" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWJavaListString]).asScala.toMap
    val model = findModel(schemas, "ModelWJavaListString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a [StringSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    nullSafeList(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Map" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWMapString]).asScala.toMap
    val model = findModel(schemas, "ModelWMapString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [MapSchema]
    val mapSchema = stringsField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    nullSafeList(mapSchema.getRequired()) shouldBe empty
  }

  it should "process Model with Java Map" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWJavaMapString]).asScala.toMap
    val model = findModel(schemas, "ModelWJavaMapString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [MapSchema]
    val mapSchema = stringsField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    nullSafeList(mapSchema.getRequired()) shouldBe empty
  }

  it should "process EchoList" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[EchoList]).asScala.toMap
    val model = findModel(schemas, "EchoList")
    model should be (defined)
    model.value.getProperties should not be (null)
    val val1Field = model.value.getProperties.get("val1")
    val1Field shouldBe a [IntegerSchema]
    val val2Field = model.value.getProperties.get("val2")
    val2Field shouldBe a [IntegerSchema]
    model.value.getRequired().asScala shouldEqual Seq("val1", "val2")
  }

  it should "process Array-Model with Scala nonOption Seq (annotated)" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWStringSeqAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWStringSeqAnnotated")
    model should be(defined)
    nullSafeList(model.value.getRequired) shouldBe empty
  }

  it should "process Array-Model with forced required Scala Option Seq (annotated)" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionStringSeqAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWOptionStringSeqAnnotated")
    model should be(defined)
    nullSafeList(model.value.getRequired) shouldEqual Seq("listOfStrings")
  }

  it should "process scala Iterable[T] classes" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[Seq[String]]).asScala.toMap
    val model = findModel(schemas, "Seq")
    model should be(defined)
    model.value.getProperties should be(null)
    model.value.getRequired should be(null)
  }

  it should "process Array-Model with forced required Scala Option Seq" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionStringSeq]).asScala.toMap
    val model = findModel(schemas, "ModelWOptionStringSeq")
    model should be(defined)
    nullSafeList(model.value.getRequired) shouldBe empty
  }

  private def findModel(schemas: Map[String, Schema[_]], name: String): Option[Schema[_]] = {
    schemas.get(name) match {
      case Some(m) => Some(m)
      case None =>
        schemas.keys.find { case k => k.startsWith(name) } match {
          case Some(key) => schemas.get(key)
          case None => schemas.values.headOption
        }
    }
  }

  private def nullSafeList[T](list: java.util.List[T]): List[T] = Option(list) match {
    case None => List[T]()
    case Some(l) => l.asScala.toList
  }

  private def nullSafeMap[K, V](map: java.util.Map[K, V]): Map[K, V] = Option(map) match {
    case None => Map[K, V]()
    case Some(m) => m.asScala.toMap
  }
}

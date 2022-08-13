package com.github.swagger.scala.converter

import io.swagger.v3.core.converter._
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media._
import models.NestingObject.{NestedModelWOptionInt, NoProperties}
import models._
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.util
import scala.collection.JavaConverters._

class ModelPropertyParserTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers with OptionValues {
  override protected def beforeEach() = {
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(true)
  }

  override protected def afterEach() = {
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(true)
  }
  
  trait TestScope {
    val converter = ModelConverters.getInstance()
  }

  it should "verify swagger-core bug 814" in new TestScope {
    val schemas = converter.readAll(classOf[CoreBug814])
    val model = schemas.get("CoreBug814")
    model should not be (null)
    model.getProperties should not be (null)
    val isFoo = model.getProperties().get("isFoo")
    isFoo should not be (null)
    isFoo shouldBe a[BooleanSchema]
  }

  it should "process Option[String] as string" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionString]).asScala.toMap
    val model = schemas.get("ModelWOptionString")
    model should be(defined)
    model.value.getProperties should not be(null)
    val stringOpt = model.value.getProperties().get("stringOpt")
    stringOpt should not be (null)
    stringOpt.isInstanceOf[StringSchema] should be(true)
    nullSafeSeq(stringOpt.getRequired) shouldBe empty
    val stringWithDataType = model.value.getProperties().get("stringWithDataTypeOpt")
    stringWithDataType should not be (null)
    stringWithDataType shouldBe a [StringSchema]
    nullSafeSeq(stringWithDataType.getRequired) shouldBe empty

    val ipAddress = model.value.getProperties().get("ipAddress")
    ipAddress should not be (null)
    ipAddress shouldBe a[StringSchema]
    ipAddress.getDescription shouldBe "An IP address"
    ipAddress.getFormat shouldBe "IPv4 or IPv6"
    nullSafeSeq(ipAddress.getRequired) shouldBe empty
  }

  it should "process Option[Model] as Model" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionModel]).asScala.toMap
    val model = schemas.get("ModelWOptionModel")
    model should be (defined)
    model.value.getProperties should not be (null)
    val modelOpt = model.value.getProperties().get("modelOpt")
    modelOpt should not be (null)
    modelOpt.get$ref() shouldEqual "#/components/schemas/ModelWOptionString"
  }

  it should "process Model with Scala BigDecimal as Number" in new TestScope {
    case class TestModelWithBigDecimal(field: BigDecimal)

    val schemas = converter.readAll(classOf[TestModelWithBigDecimal]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigDecimal")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties().get("field")
    field shouldBe a [NumberSchema]
    nullSafeSeq(model.value.getRequired) should not be empty
  }

  it should "process Model with Scala BigInt as Number" in new TestScope {
    case class TestModelWithBigInt(field: BigInt)

    val schemas = converter.readAll(classOf[TestModelWithBigInt]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigInt")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties().get("field")
    field shouldBe a [IntegerSchema]
    nullSafeSeq(model.value.getRequired) should not be empty
  }

  it should "process Model with Scala Option BigDecimal" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionBigDecimal]).asScala.toMap
    val model = schemas.get("ModelWOptionBigDecimal")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBigDecimal = model.value.getProperties().get("optBigDecimal")
    optBigDecimal should not be (null)
    optBigDecimal shouldBe a [NumberSchema]
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option BigInt" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionBigInt]).asScala.toMap
    val model = schemas.get("ModelWOptionBigInt")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBigInt = model.value.getProperties().get("optBigInt")
    optBigInt should not be (null)
    optBigInt shouldBe a [IntegerSchema]
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Int" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionInt]).asScala.toMap
    val model = schemas.get("ModelWOptionInt")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    if (RuntimeUtil.isScala3()) {
      optInt shouldBe a[ObjectSchema]
    } else {
      optInt shouldBe a[IntegerSchema]
      optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    }
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with nested Scala Option Int" in new TestScope {
    val schemas = converter.readAll(classOf[NestedModelWOptionInt]).asScala.toMap
    val model = schemas.get("NestedModelWOptionInt")
    model should be(defined)
    model.value.getProperties should not be (null)
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    if (RuntimeUtil.isScala3()) {
      optInt shouldBe a[ObjectSchema]
    } else {
      optInt shouldBe a[IntegerSchema]
      optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    }
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model without any properties" in new TestScope {
    val schemas = converter.readAll(classOf[NoProperties]).asScala.toMap
    val model = schemas.get("NoProperties")
    model should be(defined)
    model.value.getProperties should be (null)
    model.get shouldBe a[Schema[_]]
    model.get.getDescription shouldBe "An empty case class"
  }

  it should "process Model with nested Scala Option Int with Schema Override" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionIntSchemaOverride]).asScala.toMap
    val model = schemas.get("ModelWOptionIntSchemaOverride")
    model should be(defined)
    model.value.getProperties should not be (null)
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    if (RuntimeUtil.isScala3()) {
      optInt shouldBe a[ObjectSchema]
    } else {
      optInt shouldBe a[IntegerSchema]
      optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    }
    optInt.getDescription shouldBe "This is an optional int"
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Int with Schema Override" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionIntSchemaOverride]).asScala.toMap
    val model = schemas.get("ModelWOptionIntSchemaOverride")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    if (RuntimeUtil.isScala3()) {
      optInt shouldBe a[ObjectSchema]
    } else {
      optInt shouldBe a[IntegerSchema]
      optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    }
    optInt.getDescription shouldBe "This is an optional int"
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "prioritize required as specified in annotation by default" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionIntSchemaOverrideForRequired]).asScala.toMap
    val model = schemas.get("ModelWOptionIntSchemaOverrideForRequired")

    val requiredIntWithDefault = model.value.getProperties.get("requiredIntWithDefault")
    requiredIntWithDefault shouldBe an[IntegerSchema]
    requiredIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 5

    val annotatedIntWithDefault = model.value.getProperties.get("annotatedIntWithDefault")
    annotatedIntWithDefault shouldBe an[IntegerSchema]
    annotatedIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 10

    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set("annotatedOptionalInt", "requiredInt")
  }


  it should "prioritize required based on (Option or not) type when `setRequiredBasedOnAnnotation` is set" in new TestScope {
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(false)
    val schemas = converter.readAll(classOf[ModelWOptionIntSchemaOverrideForRequired]).asScala.toMap
    val model = schemas.get("ModelWOptionIntSchemaOverrideForRequired")

    val requiredIntWithDefault = model.value.getProperties.get("requiredIntWithDefault")
    requiredIntWithDefault shouldBe an[IntegerSchema]
    requiredIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 5

    val annotatedIntWithDefault = model.value.getProperties.get("annotatedIntWithDefault")
    annotatedIntWithDefault shouldBe an[IntegerSchema]
    annotatedIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 10

    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set("annotatedOptionalInt", "requiredInt",
      "annotatedRequiredInt")
  }

  it should "process Model with Scala Option Long" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionLong]).asScala.toMap
    val model = schemas.get("ModelWOptionLong")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optLong = model.value.getProperties().get("optLong")
    optLong should not be (null)
    if (RuntimeUtil.isScala3()) {
      optLong shouldBe a[ObjectSchema]
    } else {
      optLong shouldBe a[IntegerSchema]
      optLong.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"
    }
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Long with Schema Override" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionLongSchemaOverride]).asScala.toMap
    val model = schemas.get("ModelWOptionLongSchemaOverride")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optLong = model.value.getProperties().get("optLong")
    optLong should not be (null)
    optLong shouldBe a [IntegerSchema]
    optLong.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Long with Schema Int Override" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionLongSchemaIntOverride]).asScala.toMap
    val model = schemas.get("ModelWOptionLongSchemaIntOverride")
    model should be(defined)
    model.value.getProperties should not be (null)
    val optLong = model.value.getProperties().get("optLong")
    optLong should not be (null)
    optLong shouldBe a[IntegerSchema]
    optLong.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Boolean" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionBoolean]).asScala.toMap
    val model = schemas.get("ModelWOptionBoolean")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBoolean = model.value.getProperties().get("optBoolean")
    optBoolean should not be (null)
    optBoolean shouldBe a [Schema[_]]
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala Option Boolean with Schema Override" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionBooleanSchemaOverride]).asScala.toMap
    val model = schemas.get("ModelWOptionBooleanSchemaOverride")
    model should be (defined)
    model.value.getProperties should not be (null)
    val optBoolean = model.value.getProperties().get("optBoolean")
    optBoolean should not be (null)
    optBoolean shouldBe a [BooleanSchema]
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process all properties as required barring Option[_] or if overridden in annotation" in new TestScope {
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

    val requiredItems = nullSafeSeq(model.getRequired)
    requiredItems shouldBe List("forcedRequired", "required")
  }

  it should "process all properties as required barring Option[_] or if overridden in annotation (Schema annotation)" in new TestScope {
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

    val requiredItems = nullSafeSeq(model.getRequired)
    requiredItems shouldBe List("forcedRequired", "required")
  }

  it should "handle null properties from converters later in the chain" in new TestScope {
    object CustomConverter extends ModelConverter {
      override def resolve(`type`: AnnotatedType, context: ModelConverterContext, chain: util.Iterator[ModelConverter]): Schema[_] = {
        if (chain.hasNext) chain.next().resolve(`type`, context, chain) else null
      }
    }

    converter.addConverter(CustomConverter)
    converter.addConverter(new SwaggerScalaModelConverter)
    converter.readAll(classOf[Option[Int]])
  }

  it should "process Model with Scala BigDecimal with annotation" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWBigDecimalAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWBigDecimalAnnotated")
    model should be (defined)
    model.value.getProperties should not be (null)
    val fieldSchema = model.value.getProperties.get("field")
    fieldSchema shouldBe a [StringSchema]
    fieldSchema.asInstanceOf[StringSchema].getExample shouldEqual("42.0")
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process Model with Scala BigDecimal with default value annotation" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWBigDecimalAnnotatedDefault]).asScala.toMap
    val model = findModel(schemas, "ModelWBigDecimalAnnotatedDefault")
    model should be(defined)
    model.value.getProperties should not be (null)
    val fieldSchema = model.value.getProperties.get("field")
    fieldSchema shouldBe a[StringSchema]
    val stringSchema = fieldSchema.asInstanceOf[StringSchema]
    stringSchema.getDefault shouldEqual("42.0")
    stringSchema.getExample shouldEqual("42.0")

    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Model with Scala BigInt with annotation" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWBigIntAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWBigIntAnnotated")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a [StringSchema]
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process Model with Scala Enum with annotation" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWEnumAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWEnumAnnotated")
    model should be(defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a[StringSchema]
    val stringSchema = field.asInstanceOf[StringSchema]
    stringSchema.getDescription shouldEqual "enum value"
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process ListReply Model" in new TestScope {
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

  it should "process Model with Scala Seq" in new TestScope {
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
    nullSafeSeq(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Seq Int" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWSeqInt]).asScala.toMap
    val model = findModel(schemas, "ModelWSeqInt")
    model should be(defined)
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("ints")

    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("ints")

    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    if (RuntimeUtil.isScala3()) {
      arraySchema.getItems shouldBe a[ObjectSchema]
    } else {
      arraySchema.getItems shouldBe a[IntegerSchema]
    }
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    nullSafeSeq(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Seq Int (default provided in constructor)" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWSeqIntDefaulted]).asScala.toMap
    val model = findModel(schemas, "ModelWSeqIntDefaulted")
    model should be(defined)
    nullSafeSeq(model.value.getRequired) shouldBe empty

    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("ints")

    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    if (RuntimeUtil.isScala3()) {
      arraySchema.getItems shouldBe a[ObjectSchema]
    } else {
      arraySchema.getItems shouldBe a[IntegerSchema]
    }
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    nullSafeSeq(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Seq Int (annotated)" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWSeqIntAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWSeqIntAnnotated")
    model should be(defined)
    model.value.getProperties should not be (null)

    val stringsField = model.value.getProperties.get("ints")

    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)

    if (RuntimeUtil.isScala3()) {
      arraySchema.getItems shouldBe a[ObjectSchema]
    } else {
      arraySchema.getItems shouldBe a[IntegerSchema]
    }
    arraySchema.getItems.getDescription shouldBe "These are ints"
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    nullSafeSeq(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Set" in new TestScope {
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
    nullSafeSeq(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Java List" in new TestScope {
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
    nullSafeSeq(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Map" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWMapString]).asScala.toMap
    val model = findModel(schemas, "ModelWMapString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [MapSchema]
    val mapSchema = stringsField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    nullSafeSeq(mapSchema.getRequired()) shouldBe empty
  }

  it should "process Model with Java Map" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWJavaMapString]).asScala.toMap
    val model = findModel(schemas, "ModelWJavaMapString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [MapSchema]
    val mapSchema = stringsField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    nullSafeSeq(mapSchema.getRequired()) shouldBe empty
  }

  it should "process EchoList" in new TestScope {
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

  it should "process Array-Model with Scala nonOption Seq (annotated)" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWStringSeqAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWStringSeqAnnotated")
    model should be(defined)
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process Array-Model with forced required Scala Option Seq (annotated)" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionStringSeqAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWOptionStringSeqAnnotated")
    model should be(defined)
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("listOfStrings")
  }

  it should "process scala Iterable[T] classes" in new TestScope {
    val schemas = converter.readAll(classOf[Seq[String]]).asScala.toMap
    val model = findModel(schemas, "Seq")
    model should be(defined)
    model.value.getProperties should be(null)
    model.value.getRequired should be(null)
  }

  it should "process Array-Model with forced required Scala Option Seq" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWOptionStringSeq]).asScala.toMap
    val model = findModel(schemas, "ModelWOptionStringSeq")
    model should be(defined)
    nullSafeSeq(model.value.getRequired) shouldBe empty
  }

  it should "process case class with Duration field" in new TestScope {
    val schemas = converter.readAll(classOf[ModelWDuration]).asScala.toMap
    val model = findModel(schemas, "ModelWDuration")
    model should be(defined)
    model.value.getRequired.asScala shouldEqual Seq("duration")
    val props = model.value.getProperties.asScala.toMap
    props should have size 1
    props("duration") shouldBe a [Schema[_]]
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

  private def nullSafeSeq[T](list: java.util.List[T]): Seq[T] = Option(list) match {
    case None => List.empty[T]
    case Some(l) => l.asScala.toSeq
  }

  private def nullSafeMap[K, V](map: java.util.Map[K, V]): Map[K, V] = Option(map) match {
    case None => Map.empty[K, V]
    case Some(m) => m.asScala.toMap
  }
}

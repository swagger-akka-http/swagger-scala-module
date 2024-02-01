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
import scala.reflect.ClassTag

class ModelPropertyParserTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers with OptionValues {
  override protected def beforeEach() = {
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(true)
    SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue(true)
  }

  override protected def afterEach() = {
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(true)
    SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue(true)
  }

  trait TestScope {
    val converter = ModelConverters.getInstance()
  }

  class PropertiesScope[A](requiredBasedAnnotation: Boolean = true, requiredBasedDefaultValue: Boolean = true, debug: Boolean = false)(
      implicit tt: ClassTag[A]
  ) extends TestScope {
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(requiredBasedAnnotation)
    SwaggerScalaModelConverter.setRequiredBasedOnDefaultValue(requiredBasedDefaultValue)
    val schemas = converter.readAll(tt.runtimeClass).asScala.toMap
    val model = schemas.get(tt.runtimeClass.getSimpleName)
    model should be(defined)
    model.value.getProperties should not be (null)
    if (debug) {
      Json.prettyPrint(schemas)
    }
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

  it should "process Option[String] as string" in new PropertiesScope[ModelWOptionString] {
    val stringOpt = model.value.getProperties().get("stringOpt")
    stringOpt should not be (null)
    stringOpt shouldBe a[StringSchema]
    stringOpt.getRequired shouldBe null
    val stringWithDataType = model.value.getProperties().get("stringWithDataTypeOpt")
    stringWithDataType should not be (null)
    stringWithDataType shouldBe a[StringSchema]
    stringWithDataType.getRequired shouldBe null

    val ipAddress = model.value.getProperties().get("ipAddress")
    ipAddress should not be (null)
    ipAddress shouldBe a[StringSchema]
    ipAddress.getDescription shouldBe "An IP address"
    ipAddress.getFormat shouldBe "IPv4 or IPv6"
    ipAddress.getRequired shouldBe null
  }

  it should "process Option[Set[String]] as string" in new PropertiesScope[OptionSetString] {
    val values = model.value.getProperties().get("values")
    values should not be (null)
    values shouldBe an[ArraySchema]
    values.getRequired shouldBe null
    values.asInstanceOf[ArraySchema].getItems shouldBe a[StringSchema]
  }

  it should "process Option[Seq[Long]] as string" in new PropertiesScope[OptionSeqLong] {
    val values = model.value.getProperties().get("values")
    values should not be (null)
    values shouldBe an[ArraySchema]
    values.getRequired shouldBe null
    val itemSchema = values.asInstanceOf[ArraySchema].getItems
    itemSchema shouldBe an[IntegerSchema]
    itemSchema.asInstanceOf[IntegerSchema].getFormat shouldBe "int64"
  }

  it should "process Option[Model] as Model" in new PropertiesScope[ModelWOptionModel] {
    val modelOpt = model.value.getProperties().get("modelOpt")
    modelOpt should not be (null)
    modelOpt.get$ref() shouldEqual "#/components/schemas/ModelWOptionString"
  }

  it should "process Model with Scala BigDecimal as Number" in new TestScope {
    case class TestModelWithBigDecimal(field: BigDecimal)

    val schemas = converter.readAll(classOf[TestModelWithBigDecimal]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigDecimal")
    model should be(defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties().get("field")
    field shouldBe a[NumberSchema]
    nullSafeSeq(model.value.getRequired) should not be empty
  }

  it should "process Model with Scala BigInt as Number" in new TestScope {
    case class TestModelWithBigInt(field: BigInt)

    val schemas = converter.readAll(classOf[TestModelWithBigInt]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigInt")
    model should be(defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties().get("field")
    field shouldBe a[IntegerSchema]
    nullSafeSeq(model.value.getRequired) should not be empty
  }

  it should "process Model with Scala Option BigDecimal" in new PropertiesScope[ModelWOptionBigDecimal] {
    val optBigDecimal = model.value.getProperties().get("optBigDecimal")
    optBigDecimal should not be (null)
    optBigDecimal shouldBe a[NumberSchema]
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option BigInt" in new PropertiesScope[ModelWOptionBigInt] {
    val optBigInt = model.value.getProperties().get("optBigInt")
    optBigInt should not be (null)
    optBigInt shouldBe a[IntegerSchema]
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option Int" in new PropertiesScope[ModelWOptionInt] {
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    optInt shouldBe a[IntegerSchema]
    optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    model.value.getRequired shouldBe null
  }

  it should "process Model with nested Scala Option Int" in new PropertiesScope[NestedModelWOptionInt] {
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    if (RuntimeUtil.isScala3()) {
      optInt shouldBe a[ObjectSchema]
    } else {
      optInt shouldBe a[IntegerSchema]
      optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    }
    model.value.getRequired shouldBe null
  }

  it should "process Model without any properties" in new TestScope {
    val schemas = converter.readAll(classOf[NoProperties]).asScala.toMap
    val model = schemas.get("NoProperties")
    model should be(defined)
    model.value.getProperties should be(null)
    model.get shouldBe a[Schema[_]]
    model.get.getDescription shouldBe "An empty case class"
  }

  it should "process Model with nested Scala Option Int with Schema Override" in new PropertiesScope[ModelWOptionIntSchemaOverride] {
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    optInt shouldBe a[IntegerSchema]
    optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    optInt.getDescription shouldBe "This is an optional int"
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option Int with Schema Override" in new PropertiesScope[ModelWOptionIntSchemaOverride] {
    val optInt = model.value.getProperties().get("optInt")
    optInt should not be (null)
    optInt shouldBe a[IntegerSchema]
    optInt.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    optInt.getDescription shouldBe "This is an optional int"
    model.value.getRequired shouldBe null
  }

  it should "prioritize required as specified in annotation by default" in new PropertiesScope[ModelWOptionIntSchemaOverrideForRequired](
    true,
    true
  ) {
    val requiredIntWithDefault = model.value.getProperties.get("requiredIntWithDefault")
    requiredIntWithDefault shouldBe an[IntegerSchema]
    requiredIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 5

    val annotatedIntWithDefault = model.value.getProperties.get("annotatedIntWithDefault")
    annotatedIntWithDefault shouldBe an[IntegerSchema]
    annotatedIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 10

    val annotatedOptionalIntWithNoneDefault = model.value.getProperties.get("annotatedOptionalIntWithNoneDefault")
    annotatedOptionalIntWithNoneDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithNoneDefault.asInstanceOf[IntegerSchema].getDefault should be(null)

    val annotatedOptionalIntWithSomeDefault = model.value.getProperties.get("annotatedOptionalIntWithSomeDefault")
    annotatedOptionalIntWithSomeDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithSomeDefault.asInstanceOf[IntegerSchema].getDefault should be(5)

    val annotatedOptionalStringWithNoneDefault = model.value.getProperties.get("annotatedOptionalStringWithNoneDefault")
    annotatedOptionalStringWithNoneDefault shouldBe an[StringSchema]
    annotatedOptionalStringWithNoneDefault.asInstanceOf[StringSchema].getDefault should be(null)

    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set("annotatedOptionalInt", "requiredInt")
  }

  it should "prioritize required as specified in annotation and not based on default value" in new PropertiesScope[
    ModelWOptionIntSchemaOverrideForRequired
  ](
    true,
    false
  ) {
    val requiredIntWithDefault = model.value.getProperties.get("requiredIntWithDefault")
    requiredIntWithDefault shouldBe an[IntegerSchema]
    requiredIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 5

    val annotatedIntWithDefault = model.value.getProperties.get("annotatedIntWithDefault")
    annotatedIntWithDefault shouldBe an[IntegerSchema]
    annotatedIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 10

    val annotatedOptionalIntWithNoneDefault = model.value.getProperties.get("annotatedOptionalIntWithNoneDefault")
    annotatedOptionalIntWithNoneDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithNoneDefault.asInstanceOf[IntegerSchema].getDefault should be(null)

    val annotatedOptionalIntWithSomeDefault = model.value.getProperties.get("annotatedOptionalIntWithSomeDefault")
    annotatedOptionalIntWithSomeDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithSomeDefault.asInstanceOf[IntegerSchema].getDefault should be(5)

    val annotatedOptionalStringWithNoneDefault = model.value.getProperties.get("annotatedOptionalStringWithNoneDefault")
    annotatedOptionalStringWithNoneDefault shouldBe an[StringSchema]
    annotatedOptionalStringWithNoneDefault.asInstanceOf[StringSchema].getDefault should be(null)

    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set("annotatedOptionalInt", "requiredInt", "requiredIntWithDefault")
  }

  it should "prioritize required based on (Option or not) type when `setRequiredBasedOnAnnotation` is set" in new PropertiesScope[
    ModelWOptionIntSchemaOverrideForRequired
  ](requiredBasedAnnotation = false, requiredBasedDefaultValue = true) {

    val requiredIntWithDefault = model.value.getProperties.get("requiredIntWithDefault")
    requiredIntWithDefault shouldBe an[IntegerSchema]
    requiredIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 5

    val annotatedIntWithDefault = model.value.getProperties.get("annotatedIntWithDefault")
    annotatedIntWithDefault shouldBe an[IntegerSchema]
    annotatedIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 10

    val annotatedOptionalIntWithNoneDefault = model.value.getProperties.get("annotatedOptionalIntWithNoneDefault")
    annotatedOptionalIntWithNoneDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithNoneDefault.asInstanceOf[IntegerSchema].getDefault should be(null)

    val annotatedOptionalIntWithSomeDefault = model.value.getProperties.get("annotatedOptionalIntWithSomeDefault")
    annotatedOptionalIntWithSomeDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithSomeDefault.asInstanceOf[IntegerSchema].getDefault should be(5)

    val annotatedOptionalStringWithNoneDefault = model.value.getProperties.get("annotatedOptionalStringWithNoneDefault")
    annotatedOptionalStringWithNoneDefault shouldBe an[StringSchema]
    annotatedOptionalStringWithNoneDefault.asInstanceOf[StringSchema].getDefault should be(null)

    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set("annotatedOptionalInt", "requiredInt", "annotatedRequiredInt")
  }

  it should "prioritize required based on (Option or not) type when `setRequiredBasedOnAnnotation` and `requiredBasedDefaultValue` is set" in new PropertiesScope[
    ModelWOptionIntSchemaOverrideForRequired
  ](requiredBasedAnnotation = false, requiredBasedDefaultValue = false) {

    val requiredIntWithDefault = model.value.getProperties.get("requiredIntWithDefault")
    requiredIntWithDefault shouldBe an[IntegerSchema]
    requiredIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 5

    val annotatedIntWithDefault = model.value.getProperties.get("annotatedIntWithDefault")
    annotatedIntWithDefault shouldBe an[IntegerSchema]
    annotatedIntWithDefault.asInstanceOf[IntegerSchema].getDefault shouldEqual 10

    val annotatedOptionalIntWithNoneDefault = model.value.getProperties.get("annotatedOptionalIntWithNoneDefault")
    annotatedOptionalIntWithNoneDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithNoneDefault.asInstanceOf[IntegerSchema].getDefault should be(null)

    val annotatedOptionalIntWithSomeDefault = model.value.getProperties.get("annotatedOptionalIntWithSomeDefault")
    annotatedOptionalIntWithSomeDefault shouldBe an[IntegerSchema]
    annotatedOptionalIntWithSomeDefault.asInstanceOf[IntegerSchema].getDefault should be(5)

    val annotatedOptionalStringWithNoneDefault = model.value.getProperties.get("annotatedOptionalStringWithNoneDefault")
    annotatedOptionalStringWithNoneDefault shouldBe an[StringSchema]
    annotatedOptionalStringWithNoneDefault.asInstanceOf[StringSchema].getDefault should be(null)

    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set(
      "requiredInt",
      "requiredIntWithDefault",
      "annotatedRequiredInt",
      "annotatedRequiredIntWithDefault",
      "annotatedIntWithDefault",
      "annotatedOptionalInt"
    )
  }

  it should "consider fields that aren't optional required if `requiredBasedAnnotation == true`" in new PropertiesScope[
    ModelWMultipleRequiredFields
  ](
    requiredBasedAnnotation = false
  ) {
    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set("first", "second", "third")
  }

  it should "consider fields that aren't optional required" in new PropertiesScope[ModelWMultipleRequiredFields]() {
    nullSafeSeq(model.value.getRequired).toSet shouldEqual Set("first", "second", "third")
  }

  it should "process Model with Scala Option Long (Some Default)" in new PropertiesScope[ModelWOptionLongWithSomeDefault] {
    val optLong = model.value.getProperties().get("optLong")
    optLong should not be (null)
    optLong shouldBe a[IntegerSchema]
    optLong.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"
    optLong.getDefault shouldEqual Long.MaxValue
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option Long" in new PropertiesScope[ModelWOptionLong] {
    val optLong = model.value.getProperties().get("optLong")
    optLong should not be (null)
    optLong shouldBe a[IntegerSchema]
    optLong.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option Long with Schema Override" in new PropertiesScope[ModelWOptionLongSchemaOverride] {
    val optLong = model.value.getProperties().get("optLong")
    optLong should not be (null)
    optLong shouldBe a[IntegerSchema]
    optLong.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option Long with Schema Int Override" in new PropertiesScope[ModelWOptionLongSchemaIntOverride] {
    val optLong = model.value.getProperties().get("optLong")
    optLong should not be (null)
    optLong shouldBe a[IntegerSchema]
    optLong.asInstanceOf[IntegerSchema].getFormat shouldEqual "int32"
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option Boolean" in new PropertiesScope[ModelWOptionBoolean] {
    val optBoolean = model.value.getProperties().get("optBoolean")
    optBoolean should not be (null)
    optBoolean shouldBe a[Schema[_]]
    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala Option Boolean with Schema Override" in new PropertiesScope[ModelWOptionBooleanSchemaOverride] {
    val optBoolean = model.value.getProperties().get("optBoolean")
    optBoolean should not be (null)
    optBoolean shouldBe a[BooleanSchema]
    model.value.getRequired shouldBe null
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

  it should "process all properties as required barring Option[_] or if overridden in annotation (Schema annotation - old style)" in new TestScope {
    val schemas = ModelConverters
      .getInstance()
      .readAll(classOf[ModelWithOptionAndNonOption3])
      .asScala

    val model = schemas("ModelWithOptionAndNonOption3")
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

  it should "ignore required()=false on Schema annotations when setRequiredBasedOnAnnotation(false)" in new TestScope {
    SwaggerScalaModelConverter.setRequiredBasedOnAnnotation(false)
    val schemas = ModelConverters
      .getInstance()
      .readAll(classOf[ModelWithOptionAndNonOption3])
      .asScala

    val model = schemas("ModelWithOptionAndNonOption3")
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
    requiredItems shouldBe List("forcedOptional", "forcedRequired", "required")
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

  it should "process Model with Scala BigDecimal with annotation" in new PropertiesScope[ModelWBigDecimalAnnotated]() {
    val fieldSchema = model.value.getProperties.get("field")
    fieldSchema.getDeprecated shouldBe java.lang.Boolean.TRUE
    fieldSchema shouldBe a[StringSchema]
    fieldSchema.asInstanceOf[StringSchema].getExample shouldEqual ("42.0")
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "map BigDecimal to schema type 'number'" in new PropertiesScope[ModelWBigDecimalNoType]() {
    val properties = model.value.getProperties
    val fieldSchema = properties.get("field")
    fieldSchema.getDeprecated shouldBe null
    properties should have size 1

    fieldSchema shouldBe a[NumberSchema]
  }

  it should "map BigDecimal to schema type 'number' even when annotated" in new PropertiesScope[ModelWBigDecimalAnnotatedNoType]() {
    val properties = model.value.getProperties
    val fieldSchema = properties.get("field")
    properties should have size 1

    fieldSchema shouldBe a[NumberSchema]
  }

  it should "process Model with Scala BigDecimal with default value annotation" in new PropertiesScope[ModelWBigDecimalAnnotatedDefault](
    false
  ) {
    val fieldSchema = model.value.getProperties.get("field")
    fieldSchema shouldBe a[StringSchema]
    val stringSchema = fieldSchema.asInstanceOf[StringSchema]
    stringSchema.getDefault shouldEqual ("42.0")
    stringSchema.getExample shouldEqual ("42.0")
    stringSchema.getDescription shouldBe "required of annotation should be honoured"

    nullSafeSeq(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process Model with Scala BigDecimal with default value annotation (required=false)" in new PropertiesScope[
    ModelWBigDecimalAnnotatedDefaultRequiredFalse
  ](
    false
  ) {
    val fieldSchema = model.value.getProperties.get("field")
    fieldSchema shouldBe a[StringSchema]
    val stringSchema = fieldSchema.asInstanceOf[StringSchema]
    stringSchema.getDefault shouldEqual ("42.0")
    stringSchema.getExample shouldEqual ("42.0")
    stringSchema.getDescription shouldBe "required of annotation should be honoured"

    model.value.getRequired shouldBe null
  }

  it should "process Model with Scala BigInt with annotation" in new PropertiesScope[ModelWBigIntAnnotated] {
    val field = model.value.getProperties.get("field")
    field shouldBe a[StringSchema]
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process Model with Scala Enum with annotation" in new PropertiesScope[ModelWEnumAnnotated] {
    val field = model.value.getProperties.get("field")
    field shouldBe a[StringSchema]
    val stringSchema = field.asInstanceOf[StringSchema]
    stringSchema.getDescription shouldEqual "enum value"
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("field")
  }

  it should "process ListReply Model" in new TestScope {
    val schemas = converter.readAll(classOf[ListReply[String]]).asScala.toMap
    val model = findModel(schemas, "ListReply")
    model should be(defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("items")
    stringsField shouldBe an[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems shouldBe null
    arraySchema.getRequired shouldBe null
    arraySchema.getItems shouldBe a[ObjectSchema] // probably type erasure - ideally this would eval as StringSchema
    // next line used to fail (https://github.com/swagger-akka-http/swagger-akka-http/issues/171)
    Json.mapper().writeValueAsString(model.value)
  }

  it should "default to supplied schema if it can't be corrected" in new PropertiesScope[ModelWMapStringCaseClass] {
    schemas should have size 2

    model.value.getRequired shouldBe null
    val mapField = model.value.getProperties.get("maybeMapStringCaseClass")
    mapField shouldBe a[MapSchema]
    mapField.getAdditionalProperties shouldBe a[Schema[_]]
    mapField.getAdditionalProperties.asInstanceOf[Schema[_]].get$ref() shouldBe "#/components/schemas/SomeCaseClass"

    val caseClassField = schemas("SomeCaseClass")
    caseClassField shouldBe a[Schema[_]]
    caseClassField.getProperties.get("field") shouldBe an[IntegerSchema]
  }

  it should "handle Option[Map[String, Long]]" in new PropertiesScope[ModelWMapStringLong] {
    schemas should have size 1

    model.value.getRequired shouldBe null
    val mapField = model.value.getProperties.get("maybeMapStringLong")
    mapField shouldBe a[MapSchema]
    nullSafeMap(mapField.getProperties) shouldBe empty
    mapField.getAdditionalProperties shouldBe a[Schema[_]]
  }

  it should "process Model with Scala Seq" in new PropertiesScope[ModelWSeqString] {
    schemas should have size 1
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a[StringSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    arraySchema.getRequired() shouldBe null
  }

  it should "process Model with Scala Seq Int" in new PropertiesScope[ModelWSeqInt] {
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("ints")

    val stringsField = model.value.getProperties.get("ints")

    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a[IntegerSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    nullSafeSeq(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Seq Int (default provided in constructor)" in new PropertiesScope[ModelWSeqIntDefaulted] {
    nullSafeSeq(model.value.getRequired) shouldBe empty

    val stringsField = model.value.getProperties.get("ints")

    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a[IntegerSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    arraySchema.getRequired() shouldBe null
  }

  it should "process Model with Scala Seq Int (annotated)" in new PropertiesScope[ModelWSeqIntAnnotated] {
    val stringsField = model.value.getProperties.get("ints")

    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)

    arraySchema.getItems shouldBe a[IntegerSchema]
    arraySchema.getItems.getDescription shouldBe "These are ints"
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    arraySchema.getRequired() shouldBe null
  }

  it should "process Model with Scala Seq Int (annotated - old style)" in new PropertiesScope[ModelWSeqIntAnnotatedOldStyle] {
    val stringsField = model.value.getProperties.get("ints")

    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)

    arraySchema.getItems shouldBe a[IntegerSchema]
    arraySchema.getItems.getDescription shouldBe "These are ints"
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    arraySchema.getRequired() shouldBe null
  }

  it should "process Model with Scala Set" in new PropertiesScope[ModelWSetString] {
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe true
    arraySchema.getItems shouldBe a[StringSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    arraySchema.getRequired() shouldBe null
  }

  it should "process Model with Java List" in new PropertiesScope[ModelWJavaListString] {
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a[ArraySchema]
    val arraySchema = stringsField.asInstanceOf[ArraySchema]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a[StringSchema]
    nullSafeMap(arraySchema.getProperties()) shouldBe empty
    arraySchema.getRequired() shouldBe null
  }

  it should "process Model with Scala Map" in new PropertiesScope[ModelWMapString] {
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a[MapSchema]
    val mapSchema = stringsField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    mapSchema.getRequired() shouldBe null
    nullSafeSet(mapSchema.getTypes) shouldEqual Set("object")
  }

  it should "process Model with Java Map" in new PropertiesScope[ModelWJavaMapString] {
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a[MapSchema]
    val mapSchema = stringsField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    mapSchema.getRequired() shouldBe null
    nullSafeSet(mapSchema.getTypes) shouldEqual Set("object")
  }

  it should "process Model with Scala Map[Int, Long]" in new PropertiesScope[ModelWMapIntLong] {
    val mapField = model.value.getProperties.get("map")
    mapField shouldBe a[MapSchema]
    val mapSchema = mapField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    mapSchema.getRequired() shouldBe null
    nullSafeSet(mapSchema.getTypes) shouldEqual Set("object")
  }

  it should "process Model with Scala IntMap[Long]" in new PropertiesScope[ModelWIntMapLong] {
    val mapField = model.value.getProperties.get("map")
    mapField shouldBe a[MapSchema]
    val mapSchema = mapField.asInstanceOf[MapSchema]
    mapSchema.getUniqueItems() shouldBe (null)
    nullSafeMap(mapSchema.getProperties()) shouldBe empty
    mapSchema.getRequired() shouldBe null
    nullSafeSet(mapSchema.getTypes) shouldEqual Set("object")
  }

  it should "process EchoList" in new PropertiesScope[EchoList] {
    val val1Field = model.value.getProperties.get("val1")
    val1Field shouldBe a[IntegerSchema]
    val val2Field = model.value.getProperties.get("val2")
    val2Field shouldBe a[IntegerSchema]
    model.value.getRequired().asScala shouldEqual Seq("val1", "val2")
  }

  it should "process ModelWGetFunction" in new PropertiesScope[ModelWGetFunction] {
    val props = nullSafeMap(model.value.getProperties)
    props should have size 1
    val amountField = props.get("amount").value
    amountField shouldBe a[IntegerSchema]
    amountField.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"

    nullSafeSeq(model.value.getRequired) shouldEqual Seq("amount")
  }

  it should "process ModelWGetFunction with optional field" in new PropertiesScope[ModelWGetFunctionWithOptionalField] {
    val props = nullSafeMap(model.value.getProperties)
    props should have size 1
    val amountField = props.get("amount").value
    amountField shouldBe a[IntegerSchema]
    amountField.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"

    model.value.getRequired shouldBe null
  }

  it should "process ModelWJacksonAnnotatedGetFunction" in new PropertiesScope[ModelWJacksonAnnotatedGetFunction] {
    val props = nullSafeMap(model.value.getProperties)
    props should have size 1
    val amountField = props.get("amount").value
    amountField shouldBe a[IntegerSchema]
    amountField.asInstanceOf[IntegerSchema].getFormat shouldEqual "int64"

    nullSafeSeq(model.value.getRequired) shouldEqual Seq("amount")
  }

  it should "process Array-Model with Scala nonOption Seq (annotated)" in new PropertiesScope[ModelWStringSeqAnnotated] {
    model.value.getRequired shouldBe null
  }

  it should "process Array-Model with forced required Scala Option Seq (annotated)" in new PropertiesScope[ModelWOptionStringSeqAnnotated] {
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("listOfStrings")
  }

  it should "process scala Iterable[T] classes" in new TestScope {
    val schemas = converter.readAll(classOf[Seq[String]]).asScala.toMap
    val model = findModel(schemas, "Seq")
    model should be(defined)
    model.value.getProperties should be(null)
    model.value.getRequired should be(null)
  }

  it should "process Array-Model with forced required Scala Option Seq" in new PropertiesScope[ModelWOptionStringSeq] {
    model.value.getRequired shouldBe null
  }

  it should "process case class with Duration field" in new PropertiesScope[ModelWDuration] {
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("duration")
    val props = nullSafeMap(model.value.getProperties)
    props should have size 1
    props("duration") shouldBe a[Schema[_]]
  }

  it should "process DataExampleClass" in new PropertiesScope[DataExampleClass] {
    nullSafeSeq(model.value.getRequired) shouldEqual Seq("data")
    val props = nullSafeMap(model.value.getProperties)
    props should have size 1
    props("data") shouldBe a[Schema[_]]
  }

  it should "process sealed abstract class" in new TestScope {
    val schemas = converter.readAll(classOf[Animal]).asScala.toMap
    val catModel = findModel(schemas, "Cat")
    catModel should be(defined)
    val catProps = nullSafeMap(catModel.value.getProperties)
    catProps should have size 3
    catProps.get("name").value shouldBe a[StringSchema]
    catProps.get("age").value shouldBe a[IntegerSchema]
    catProps.get("animalType").value shouldBe a[StringSchema]
    nullSafeSeq(catModel.value.getRequired) shouldEqual Seq("age", "animalType", "name")
    val dogModel = findModel(schemas, "Dog")
    dogModel should be(defined)
    val dogProps = nullSafeMap(dogModel.value.getProperties)
    dogProps should have size 2
    dogProps.get("name").value shouldBe a[StringSchema]
    dogProps.get("animalType").value shouldBe a[StringSchema]
    nullSafeSeq(dogModel.value.getRequired) shouldEqual Seq("animalType", "name")
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

  private def nullSafeSet[T](list: java.util.Set[T]): Set[T] = Option(list) match {
    case None => Set.empty[T]
    case Some(l) => l.asScala.toSet
  }

  private def nullSafeMap[K, V](map: java.util.Map[K, V]): Map[K, V] = Option(map) match {
    case None => Map.empty[K, V]
    case Some(m) => m.asScala.toMap
  }
}

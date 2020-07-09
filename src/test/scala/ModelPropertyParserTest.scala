import java.lang.annotation.Annotation
import java.lang.reflect.Type
import java.util

import io.swagger.converter._
import io.swagger.models.Model
import io.swagger.models.properties._
import io.swagger.scala.converter.SwaggerScalaModelConverter
import io.swagger.util.Json
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
    val isFoo = model.getProperties().get("isFoo")
    isFoo should not be (null)
    isFoo.isInstanceOf[BooleanProperty] should be (true)
  }

  it should "process Option[String] as string" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionString]).asScala.toMap
    val model = schemas.get("ModelWOptionString")
    model should be ('defined)
    val stringOpt = model.value.getProperties().get("stringOpt")
    stringOpt should not be (null)
    stringOpt.isInstanceOf[StringProperty] should be (true)
    stringOpt.getRequired should be (false)
    val stringWithDataType = model.value.getProperties().get("stringWithDataTypeOpt")
    stringWithDataType should not be (null)
    stringWithDataType.isInstanceOf[StringProperty] should be (true)
    stringWithDataType.getRequired should be (false)
  }

  it should "process Option[Model] as Model" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionModel]).asScala.toMap
    val model = schemas.get("ModelWOptionModel")
    model should be ('defined)
    val modelOpt = model.value.getProperties().get("modelOpt")
    modelOpt should not be (null)
    modelOpt.isInstanceOf[RefProperty] should be (true)
  }

  it should "process Model with Scala BigDecimal as Number" in {
    case class TestModelWithBigDecimal(field: BigDecimal)

    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[TestModelWithBigDecimal]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigDecimal")
    model should be ('defined)
    val modelOpt = model.value.getProperties().get("field")
    modelOpt shouldBe a [DecimalProperty]
    modelOpt.getRequired should be (true)
  }

  it should "process Model with Scala BigInt as Number" in {
    case class TestModelWithBigInt(field: BigInt)

    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[TestModelWithBigInt]).asScala.toMap
    val model = findModel(schemas, "TestModelWithBigInt")
    model should be ('defined)
    val modelOpt = model.value.getProperties().get("field")
    modelOpt shouldBe a [BaseIntegerProperty]
    modelOpt.getRequired should be (true)
  }

  it should "process Model with Scala Option BigDecimal" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBigDecimal]).asScala.toMap
    val model = schemas.get("ModelWOptionBigDecimal")
    model should be ('defined)
    val optBigDecimal = model.value.getProperties().get("optBigDecimal")
    optBigDecimal should not be (null)
    optBigDecimal shouldBe a [DecimalProperty]
    optBigDecimal.getRequired should be (false)
  }

  it should "process Model with Scala Option BigInt" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBigInt]).asScala.toMap
    val model = schemas.get("ModelWOptionBigInt")
    model should be ('defined)
    val optBigDecimal = model.value.getProperties().get("optBigInt")
    optBigDecimal should not be (null)
    optBigDecimal shouldBe a [BaseIntegerProperty]
    optBigDecimal.getRequired should be (false)
  }

  it should "process Model with Scala Option Boolean" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWOptionBoolean]).asScala.toMap
    val model = schemas.get("ModelWOptionBoolean")
    model should be ('defined)
    val optBoolean = model.value.getProperties().get("optBoolean")
    optBoolean should not be (null)
    optBoolean shouldBe a [ObjectProperty]
    optBoolean.getRequired should be (false)
  }

  it should "process Model with Scala BigDecimal with annotation" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWBigDecimalAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWBigDecimalAnnotated")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a [StringProperty]
    field.getRequired should be (true)
  }

  it should "process Model with Scala BigInt with annotation" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWBigIntAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWBigIntAnnotated")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a [StringProperty]
    field.getRequired should be (true)
  }

  it should "process Model with Scala Enum with annotation" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWEnumAnnotated]).asScala.toMap
    val model = findModel(schemas, "ModelWEnumAnnotated")
    model should be (defined)
    model.value.getProperties should not be (null)
    val field = model.value.getProperties.get("field")
    field shouldBe a [StringProperty]
    field.getRequired should be (true)
  }


  it should "process all properties as required barring Option[_] or if overridden in annotation" in {
    val schemas = ModelConverters
      .getInstance()
      .readAll(classOf[ModelWithOptionAndNonOption])
      .asScala

    val model = schemas("ModelWithOptionAndNonOption")
    model should not be (null)

    val optional = model.getProperties().get("optional")
    optional.getRequired should be (false)

    val required = model.getProperties().get("required")
    required.getRequired should be (true)

    val forcedRequired = model.getProperties().get("forcedRequired")
    forcedRequired.getRequired should be (true)

    val forcedOptional = model.getProperties().get("forcedOptional")
    forcedOptional.getRequired should be (false)
  }

  it should "handle null properties from converters later in the chain" in {
    object CustomConverter extends ModelConverter {
      def resolve(`type`: Type, context: ModelConverterContext, chain: util.Iterator[ModelConverter]): Model = {
        if (chain.hasNext) chain.next().resolve(`type`, context, chain) else null
      }

      def resolveProperty(`type`: Type, context: ModelConverterContext, annotations: Array[Annotation], chain: util.Iterator[ModelConverter]): Property = {
        null
      }
    }

    val converter = new ModelConverters()
    converter.addConverter(CustomConverter)
    converter.addConverter(new SwaggerScalaModelConverter)
    converter.readAll(classOf[Option[Int]])
  }

  it should "process ListReply Model" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ListReply[String]]).asScala.toMap
    val model = findModel(schemas, "ListReply")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("items")
    stringsField shouldBe a [ArrayProperty]
    val arraySchema = stringsField.asInstanceOf[ArrayProperty]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a [ObjectProperty]
    //next line fails with jackson 2.10.3, 2.10.4 and 2.11.1
    Json.mapper().writeValueAsString(model.value)
  }

  it should "process Model with Scala Seq" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWSeqString]).asScala.toMap
    val model = findModel(schemas, "ModelWSeqString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [ArrayProperty]
    val arraySchema = stringsField.asInstanceOf[ArrayProperty]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a [StringProperty]
    //nullSafeMap(arraySchema.getProperties()) shouldBe empty
    //nullSafeList(arraySchema.getRequired()) shouldBe empty
  }

  it should "process Model with Scala Set" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWSetString]).asScala.toMap
    val model = findModel(schemas, "ModelWSetString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [ArrayProperty]
    val arraySchema = stringsField.asInstanceOf[ArrayProperty]
    arraySchema.getUniqueItems() shouldBe true
    arraySchema.getItems shouldBe a [StringProperty]
  }

  it should "process Model with Java List" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWJavaListString]).asScala.toMap
    val model = findModel(schemas, "ModelWJavaListString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [ArrayProperty]
    val arraySchema = stringsField.asInstanceOf[ArrayProperty]
    arraySchema.getUniqueItems() shouldBe (null)
    arraySchema.getItems shouldBe a [StringProperty]
  }

  it should "process Model with Scala Map" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWMapString]).asScala.toMap
    val model = findModel(schemas, "ModelWMapString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [MapProperty]
    val mapSchema = stringsField.asInstanceOf[MapProperty]
    mapSchema.getAdditionalProperties shouldBe a[StringProperty]
  }

  it should "process Model with Java Map" in {
    val converter = ModelConverters.getInstance()
    val schemas = converter.readAll(classOf[ModelWJavaMapString]).asScala.toMap
    val model = findModel(schemas, "ModelWJavaMapString")
    model should be (defined)
    model.value.getProperties should not be (null)
    val stringsField = model.value.getProperties.get("strings")
    stringsField shouldBe a [MapProperty]
    val mapSchema = stringsField.asInstanceOf[MapProperty]
    mapSchema.getAdditionalProperties shouldBe a[StringProperty]
  }

  private def findModel(schemas: Map[String, Model], name: String): Option[Model] = {
    schemas.get(name) match {
      case Some(m) => Some(m)
      case None =>
        schemas.keys.find { case k => k.startsWith(name) } match {
          case Some(key) => schemas.get(key)
          case None => schemas.values.headOption
        }
    }
  }
}

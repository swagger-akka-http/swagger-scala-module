package com.github.swagger.scala.converter

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.annotations.media.Schema
import models.ModelWGetFunction
import org.scalatest.{BeforeAndAfterAll, OptionValues}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.collection.JavaConverters._

abstract class AmountDescriptionMixIn {
  @Schema(description = "set via customizer") def amount: Long
}

class ObjectMapperCustomizerTest extends AnyFlatSpec with Matchers with OptionValues with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    SwaggerScalaModelConverter.setObjectMapperCustomizer()
    ModelConverters.reset()
  }

  private def scalaModuleRegistered(mapper: ObjectMapper): Boolean = {
    mapper.getRegisteredModuleIds.asScala.exists(_.toString.startsWith("com.fasterxml.jackson.module.scala"))
  }

  "SwaggerScalaModelConverter" should "not register DefaultScalaModule on swagger-core's Json.mapper()" in {
    ModelConverters.getInstance().readAll(classOf[ModelWGetFunction])
    scalaModuleRegistered(Json.mapper()) shouldBe false
    scalaModuleRegistered(SwaggerScalaModelConverter.createObjectMapper()) shouldBe true
  }

  it should "give the customizer a mapper with DefaultScalaModule registered and use what it returns" in {
    var seen: Option[ObjectMapper] = None
    SwaggerScalaModelConverter.setObjectMapperCustomizer { mapper =>
      seen = Some(mapper)
      mapper.addMixIn(classOf[ModelWGetFunction], classOf[AmountDescriptionMixIn])
    }
    ModelConverters.reset()
    try {
      val schemas = ModelConverters.getInstance().readAll(classOf[ModelWGetFunction]).asScala
      scalaModuleRegistered(seen.value) shouldBe true
      val amount = schemas("ModelWGetFunction").getProperties.get("amount")
      amount.getDescription shouldEqual "set via customizer"
      scalaModuleRegistered(Json.mapper()) shouldBe false
    } finally {
      SwaggerScalaModelConverter.setObjectMapperCustomizer()
      ModelConverters.reset()
    }
    val amount = ModelConverters.getInstance().readAll(classOf[ModelWGetFunction]).asScala("ModelWGetFunction").getProperties.get("amount")
    amount.getDescription shouldBe null
  }
}

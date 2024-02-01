package com.github.swagger.scala.converter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object ErasureHelperTest {
  private trait SuperType {
    def getFoo: String
  }
}

class ErasureHelperTest extends AnyFlatSpec with Matchers {
  "ErasureHelper" should "handle MyTrait" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[ErasureHelperTest.SuperType]) shouldBe empty
  }
  it should "handle OptionLong" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[OptionLong]) shouldBe Map("value" -> classOf[Long])
  }
  it should "handle OptionSeqLong" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[OptionSeqLong]) shouldBe Map("values" -> classOf[Long])
  }
  it should "handle Nested.OptionSeqLong" in {
    val expected = if (RuntimeUtil.isScala3()) Map.empty[String, Class[_]] else Map("values" -> classOf[Long])
    ErasureHelper.erasedOptionalPrimitives(classOf[Nested.OptionSeqLong]) shouldBe expected
  }
  it should "handle SeqOptionLong" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[SeqOptionLong]) shouldBe Map("values" -> classOf[Long])
  }
  it should "handle OptionSeqOptionLong" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[OptionSeqOptionLong]) shouldBe Map("values" -> classOf[Long])
  }
  it should "handle OptionSetString" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[OptionSetString]) shouldBe Map.empty
  }
}

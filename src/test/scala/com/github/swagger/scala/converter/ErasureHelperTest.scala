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
  it should "handle OptionSeqLong" in {
    val expected = if (RuntimeUtil.isScala3()) Map.empty[String, Class[_]] else Map("values" -> classOf[Long])
    ErasureHelper.erasedOptionalPrimitives(classOf[OptionSeqLong]) shouldBe expected
  }
  it should "handle SeqOptionLong" in {
    val expected = if (RuntimeUtil.isScala3()) Map.empty[String, Class[_]] else Map("values" -> classOf[Long])
    ErasureHelper.erasedOptionalPrimitives(classOf[SeqOptionLong]) shouldBe expected
  }
}

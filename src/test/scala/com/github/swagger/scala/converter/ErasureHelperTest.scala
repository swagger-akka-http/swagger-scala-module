package com.github.swagger.scala.converter

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

object ErasureHelperTest {
  private trait SuperType {
    def getFoo: String
  }
}

class ErasureHelperTest extends AnyFlatSpec with Matchers {
  TestModelRegistration.register()
  ScalaModelRegistry.register[ErasureHelperTest.SuperType]

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
    ErasureHelper.erasedOptionalPrimitives(classOf[Nested.OptionSeqLong]) shouldBe Map("values" -> classOf[Long])
  }
  it should "handle SeqOptionLong" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[SeqOptionLong]) shouldBe Map("values" -> classOf[Long])
  }
  it should "handle OptionSeqOptionLong" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[OptionSeqOptionLong]) shouldBe Map("values" -> classOf[Long])
  }
  it should "handle a generic class" in {
    // a class has one runtime class for all of its type arguments, so the element types that depend on a type parameter
    // cannot be resolved - only limit, whose type is the same for every instantiation of PagedReply
    ErasureHelper.erasedOptionalPrimitives(classOf[PagedReply[_]]) shouldBe Map("limit" -> classOf[Int])
  }

  it should "handle OptionSetString" in {
    ErasureHelper.erasedOptionalPrimitives(classOf[OptionSetString]) shouldBe Map.empty
  }
}

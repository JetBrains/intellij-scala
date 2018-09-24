package org.jetbrains.plugins.scala.lang.implicits

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Anton Yalyshev
  * @since 07/09/18
  */

class ImplicitsTest extends TypeInferenceTestBase {

  def testSCL13205(): Unit = {
    doTest(
      s"""
         |import scala.language.implicitConversions
         |
         |case class Echo(s:String)
         |
         |trait Echos {
         |  implicit def string(s:String):Echo = Echo(s)
         |  def echo(e:Echo):Unit
         |}
         |
         |object Test {
         |  def test3(E:Echos) = {
         |    import E.{string=>_, _}
         |    implicit def string1(s:String):Echo = Echo(s+" --- Custom implicit conversion")
         |    // works, but IDEA doesn't recognize
         |    echo(${START}"sss"$END)
         |  }
         |}
         |//Echo
      """.stripMargin)
  }
}

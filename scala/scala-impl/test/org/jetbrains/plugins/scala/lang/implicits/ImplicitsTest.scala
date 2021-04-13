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

  def testSCL14535(): Unit = {
    doTest(
      s"""
         |object Repro {
         |  object Builder {
         |    class Step2[P, S]
         |    class Step3[P, S, B] {
         |      def run(): this.type = this
         |    }
         |    implicit def step2ToStep3[X, P, S](b: X)(implicit ev: X => Step2[P, S]): Step3[P, S, Unit] = new Step3[P, S, Unit]
         |  }
         |  val step2 = new Builder.Step2[String, Double]
         |
         |  ${START}step2.run()$END
         |}
         |//Repro.Builder.Step3[String, Double, Unit]
       """.stripMargin
    )
  }

  def testSCL7809(): Unit = doTest {
    """
      |class SCL7809 {
      |  implicit def longToString(s: Long): String = s.toString
      |  def useString(s: String) = s
      |  def useString(d: Boolean) = d
      |  /*start*/useString(1)/*end*/
      |}
      |//String
    """.stripMargin.trim
  }
}
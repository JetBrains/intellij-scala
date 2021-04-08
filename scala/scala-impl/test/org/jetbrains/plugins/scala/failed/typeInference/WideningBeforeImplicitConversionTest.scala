package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Nikolay.Tropin
  */
class WideningBeforeImplicitConversionTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testScl9523(): Unit = {
    val text =
      s"""import scala.language.{existentials, implicitConversions}
        |
        |object Main extends App {
        |  Tag("key", Set[Value[Value.T]](${START}123$END))
        |}
        |
        |case class Tag(key: String, values: Set[Value[Value.T]])
        |
        |object Value {
        |  type T = X forSome { type X <: AnyVal }
        |
        |  implicit def number2Value(v: Long): Value[T] = LongValue(v)
        |
        |  def apply(v: Long): Value[T] = LongValue(v)
        |}
        |
        |sealed trait Value[+T <: AnyVal] {
        |  def v: T
        |}
        |
        |case class LongValue(v: Long) extends Value[Long]
        |
        |//Value.Value.T""".stripMargin
    doTest(text)
  }

  def testSCL8234(): Unit = {
    doTest(
      s"""object Test {
         |  implicit class Gram(number: Double) {
         |    def g: Gram = this
         |
         |    def kg: Gram = Gram(number * 1000)
         |  }
         |
         |  def main(args: Array[String]): Unit = {
         |    ${START}1.kg$END
         |  }
         |}
         |//Gram""".stripMargin)
  }
}

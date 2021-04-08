package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Alefas
  * @since 23/03/16
  */
class BoundsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL4373(): Unit = doTest() //blinking test

  def testSCL5215(): Unit = doTest()

  def testSCL7085(): Unit = doTest()

  def testSCL9755(): Unit = {
    val text =
      s"""object IntelliJ {
        |  trait Base[T]
        |  case object StringExample extends Base[String]
        |
        |  implicit val baseStringEvidence = StringExample
        |
        |  def apply[T: Base](semantic: Base[T]): T = semantic match {
        |    case StringExample => $START"string"$END
        |  }
        |}
        |//T""".stripMargin
    doTest(text)
  }

  def testSCL5186(): Unit ={
    doTest(
      s"""
        |def foo[V <: U, U](v: V, u: U) = u
        |${START}foo(1, "asa")$END
        |//Any
      """.stripMargin)
  }
}

package org.jetbrains.plugins.scala.lang.typeInference

class BoundsTest extends TypeInferenceTestBase {

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL4373(): Unit = doTest()

  def testSCL5186(): Unit ={
    doTest(
      s"""
         |class Wrapper {
         |  def foo[V <: U, U](v: V, u: U) = u
         |  ${START}foo(1, "asa")$END
         |}
         |//String
         |""".stripMargin)
  }

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
}

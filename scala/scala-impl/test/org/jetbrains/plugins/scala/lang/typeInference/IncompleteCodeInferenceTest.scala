package org.jetbrains.plugins.scala.lang.typeInference

class IncompleteCodeInferenceTest extends TypeInferenceTestBase {
  def test_erroneous_partial_case_class_param_type(): Unit = doTest(
    s"""
       |object TestCtx {
       |  case class Test[T](wrapyed: List[T<"])
       |  val x = ${START}Test(List.empty[Int])$END
       |}
       |//TestCtx.Test[Nothing]
       |""".stripMargin
  )

  def test_erroneous_partial_case_class_default_arg(): Unit = doTest(
    s"""
       |object TestCtx {
       |  case class Test(wrapyed: Int = ")
       |  val x = ${START}Test(3)$END
       |}
       |//TestCtx.Test
       |""".stripMargin
  )
}

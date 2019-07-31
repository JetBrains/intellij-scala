package org.jetbrains.plugins.scala.failed.typeInference

class ImplicitParametersTest extends ImplicitParametersTestBase {
  def testSCL15862(): Unit = checkNoImplicitParameterProblems(
    s"""
       |class ImplicitDep()
       |class X {
       |  def callWithImplicitParam(implicit a: ImplicitDep): String = "test"
       |}
       |class TestA(x: X, private implicit val dep: ImplicitDep) {
       |  ${START}x.callWithImplicitParam$END
       |}
       |""".stripMargin
  )
}

package org.jetbrains.plugins.scala.lang.typeInference

class JavaMethodReferenceAsLambdaParamInferenceTest extends TypeInferenceTestBase {
  def testSCL13314(): Unit = doTest(
    s"""
      |val pw: java.io.PrintWriter = ???
      |List(1, 2, 3).foreach(${START}pw.println$END)
      |//Int => Unit""".stripMargin
  )
}

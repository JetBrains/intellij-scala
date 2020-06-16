package org.jetbrains.plugins.scala.lang.typeInference

class HKTypeParamLubTest extends TypeInferenceTestBase {
  def testSCL17527(): Unit = doTest(
    s"""
       |def foo[F[_]](): Int = {
       |  val fu: F[Unit] = ???
       |  val fs: F[String] = ???
       |  val res = ${START}if (true) fu else fs$END
       |  1
       |}
       |//F[_ >: Unit with String]
       |""".stripMargin
  )
}

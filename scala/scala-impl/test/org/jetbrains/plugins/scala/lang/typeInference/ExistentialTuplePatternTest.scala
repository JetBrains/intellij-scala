package org.jetbrains.plugins.scala.lang.typeInference

class ExistentialTuplePatternTest extends TypeInferenceTestBase{
  def testSCL16820(): Unit = doTest(
    s"""
       |trait a[A, B] {
       |    def returnOption(): (Option[A], Option[B])
       |}
       |
       |class august {
       |  def august(test: a[_, _]): Unit = {
       |    val (ra, rb) = test.returnOption()
       |    ${START}ra$END
       |  }
       |}
       |//Option[Any]
       |""".stripMargin
  )
}

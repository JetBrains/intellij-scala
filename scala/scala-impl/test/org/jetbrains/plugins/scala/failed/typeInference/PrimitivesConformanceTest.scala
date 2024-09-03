package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class PrimitivesConformanceTest extends TypeInferenceTestBase {

  //SCL-5358
  def testSCL5358(): Unit = assertErrorsText(
      """final val x = 0
        |val y: Byte = x
        |""".stripMargin,
    """Error(x,Expression of type Int doesn't conform to expected type Byte)"""
  )

  //SCL-19295
  def testSCL19295(): Unit = {
    doTest(
      s"""val byte: Byte = 1
        |${START}~byte$END
        |//Int
        |""".stripMargin
    )
  }
}

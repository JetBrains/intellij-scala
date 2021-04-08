package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * Created by kate on 3/24/16.
  */

class OperatorsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL5723(): Unit = doTest {
    """
      |trait Foo {
      |  def `\\`(bar: Int): Int = 1
      |
      |  def test(f: Foo) {
      |    /*start*/f \ 33/*end*/
      |  }
      |}
      |//Int
    """.stripMargin.trim
  }
}

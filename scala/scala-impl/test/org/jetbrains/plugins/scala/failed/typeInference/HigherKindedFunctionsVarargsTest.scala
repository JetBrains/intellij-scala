package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class HigherKindedFunctionsVarargsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL11489(): Unit = {
    doTest(
      s"""
         |  trait AAA {
         |    def whatever(i: Int): Unit = ()
         |  }
         |
         |  trait BBB[+A] extends AAA
         |
         |  object DDD {
         |    def aa(y: AAA): Unit = println("hi there")
         |  }
         |
         |  class CCC[X[_] <: BBB[_]] {
         |    def a[B](x: X[B]): Unit = {
         |      DDD.aa(${START}x$END)
         |      ()
         |    }
         |  }
         |//AAA
      """.stripMargin)
  }
}

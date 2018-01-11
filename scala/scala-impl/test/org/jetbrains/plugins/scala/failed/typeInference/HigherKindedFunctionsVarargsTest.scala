package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author mucianm 
  * @since 07.04.16.
  */
@Category(Array(classOf[PerfCycleTests]))
class HigherKindedFunctionsVarargsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL4789(): Unit = {
    doTest(
      s"""
        |def a[T](q: String, args: Any*): Option[T] = null
        |def b[T](f: (String, Any*) => Option[T]) = null
        |b(${START}a$END)
        |//(String, Any*) => Option[Nothing]
      """.stripMargin)
  }

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

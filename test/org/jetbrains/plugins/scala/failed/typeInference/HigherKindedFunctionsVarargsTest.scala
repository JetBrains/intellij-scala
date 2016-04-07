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

  def testSCL4789(): Unit = {
    doTest(
      s"""
        |def a[T](q: String, args: Any*): Option[T] = null
        |def b[T](f: (String, Any*) => Option[T]) = null
        |b(${START}a$END)
        |//(String, Any*) => Option[Nothing]
      """.stripMargin)
  }

}

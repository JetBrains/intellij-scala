package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class SingletonTypeTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL9053() = {
    val text =
      s"""class Base
        |class Sub extends Base
        |
        |class B[B <: Base](val a: B)
        |
        |class Test {
        |  val a: Sub = null
        |  val b: B[a.type] = ${START}new B(a)$END
        |}
        |//B[Test.this.a.type]""".stripMargin
    doTest(text)
  }
}

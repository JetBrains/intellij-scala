package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ContravarianceTest extends TypeInferenceTestBase {
  def testScl4123() = {
    val text =
      s"""object Test {
        |  class A
        |  class C
        |  class B extends C
        |
        |  class Z[-T] //in case of covariant or invariant, all is ok
        |
        |  def goo[A, BB >: A](x: A): Z[BB] = new Z[BB]
        |  val zzzzzz = goo(new B) //here type is Z[Any], according to the compiler it's Z[B]
        |  ${START}zzzzzz$END
        |}
        |
        |//Test.Z[B]""".stripMargin
    doTest(text)
  }
}

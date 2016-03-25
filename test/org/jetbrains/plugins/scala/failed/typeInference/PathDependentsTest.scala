package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 22/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class PathDependentsTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL7017(): Unit = {
    doTest(
      """
        |class SCL7017 {
        |  abstract class A
        |  case object B extends A
        |  case object C extends A
        |  case class X[T <: A](o: T, n: Int) {
        |    def +(that: X[o.type]): Int = 1
        |  }
        |  /*start*/X(B, 1) + X(B, 2)/*end*/
        |}
        |//Int
      """.stripMargin.trim
    )
  }

  def testSCL7954(): Unit = doTest()

  def testSCL9681(): Unit = doTest()

  def testSCL6143(): Unit = doTest()
}

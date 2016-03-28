package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 28/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class LocalTypInferenceTest extends TypeInferenceTestBase {
  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9671(): Unit = doTest {
    """
      |object SCL9671 {
      |  class U
      |  class TU extends U
      |  class F[T <: U]
      |  class A[T <: U](x: F[T], y: Set[T] = Set.empty[T])
      |
      |  val f: F[TU] = new F
      |  /*start*/new A(f)/*end*/
      |}
      |//SCL9671.A[SCL9671.TU]
    """.stripMargin.trim
  }
}

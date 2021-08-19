package org.jetbrains.plugins.scala.lang.typeInference

class VariousResolveIssuesTest extends TypeInferenceTestBase {
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

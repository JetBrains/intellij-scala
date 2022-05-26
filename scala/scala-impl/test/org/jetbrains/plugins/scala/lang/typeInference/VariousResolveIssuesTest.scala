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

  def testSCL9525(): Unit = doTest(
    """
      |object SCL9525 {
      |
      |  case class Quantity(raw: Int)
      |
      |  case class Dollars(raw: BigDecimal)
      |
      |  object Data {
      |    implicit val dollarsOrdering: Ordering[Dollars] = implicitly[Ordering[BigDecimal]].on[Dollars](_.raw)
      |    implicit val qtyOrdering: Ordering[Quantity] = implicitly[Ordering[Int]].on[Quantity](_.raw)
      |
      |    val d = Dollars(BigDecimal(0))
      |    val q = Quantity(1)
      |  }
      |
      |  import Data._
      |
      |  object NoProblem {
      |
      |    import dollarsOrdering.mkOrderingOps
      |
      |    d <= d
      |  }
      |
      |  object Problem {
      |
      |    import dollarsOrdering.mkOrderingOps
      |    import qtyOrdering.mkOrderingOps
      |
      |    // Neither line compiles, but IDEA thinks the second line is ok.
      |    // I would have thought it was ok too...
      |    //  d <= d
      |    //  q <= q
      |  }
      |
      |  object Problem2 {
      |
      |    import dollarsOrdering.{mkOrderingOps => dollarsOrderingOps}
      |    import qtyOrdering.{mkOrderingOps => qtyOrderingOps}
      |
      |    // Both lines compile, but IDEA thinks both lines are errors
      |    val x = d <= d
      |    // cannot resolve symbol <=
      |    val y = q <= q // cannot resolve symbol <=
      |  }
      |
      |  object Problem2Simple {
      |
      |    import dollarsOrdering.{mkOrderingOps => dollarsOrderingOps}
      |
      |    // only one implicit class in scope, but IDEA thinks it's an error
      |    val z = d <= d // cannot resolve symbol <=
      |  }
      |
      |  /*start*/(Problem2.x, Problem2.y, Problem2Simple.z)/*end*/
      |}
      |//(Boolean, Boolean, Boolean)
      |""".stripMargin
  )
}

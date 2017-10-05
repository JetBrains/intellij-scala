package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Alefas
  * @since 28/03/16
  */
@Category(Array(classOf[PerfCycleTests]))
class LocalTypeInferenceTest extends TypeInferenceTestBase {
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

  def testSCL6482(): Unit = doTest {
    """
      |object SCL6482 {
      |  class Foo[T, U <: T](u: U)
      |  def foo[T](t: T) = new Foo(t)
      |
      |  /*start*/foo(1)/*end*/
      |}
      |//SCL6482.Foo[Int, Int]
    """.stripMargin.trim
  }
  
  def testSCL6233(): Unit = doTest {
    """
      |  class EnumSetTest {
      |
      |    object Enum extends Enumeration {
      |      val e1, e2, e3, e4 = Value
      |    }
      |
      |    def mapOfSets: Map[Enum.Value, Set[Long]] = /*start*/(Enum.values map (e ⇒ e → Set(1, 2, 4))).toMap/*end*/
      |
      |  }
      |//Map[EnumSetTest.this.Enum.Value, Set[Long]]""".stripMargin
  }

  def testSCL7970(): Unit = doTest(
    """
      |trait Set[-A]{
      |  private val self = this
      |
      |  def contains(e: A): Boolean
      |
      |  def x[B](other: Set[B]): Set[(A, B)] = new Set[(A, B)] {
      |    override def contains(e: (A, B)): Boolean = (self /*start*/contains/*end*/ e._1) && (other contains e._2)
      |  }
      |}
      |//(A) => Boolean
    """.stripMargin)
}

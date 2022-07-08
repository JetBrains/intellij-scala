package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

class LocalTypeInferenceTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

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

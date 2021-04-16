package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase

/**
  * @author Alefas
  * @since 22/03/16
  */
class ExistentialsTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  override def folderPath: String = super.folderPath + "bugs5/"

  def testSCL9474(): Unit = doTest {
    """
      |object Foo {
      |  trait Sys[L <: Sys[L]]
      |
      |  trait SkipMap[E <: Sys[E], A, B] {
      |    def add(entry: (A, B)): Option[B]
      |  }
      |
      |  trait Output[C <: Sys[C]]
      |
      |  class OutputImpl[S <: Sys[S]](proc: Proc[S]) extends Output[S] {
      |    import proc.{outputs => map}
      |
      |    def add(key: String, value: Output[S]): Unit =
      |      map.add(key -> /*start*/value/*end*/)   // type mismatch here
      |  }
      |
      |  trait Proc[J <: Sys[J]] {
      |    def outputs: SkipMap[J, String, Output[J]]
      |  }
      |}
      |
      |//Foo.Output[J]
    """.stripMargin.trim
  }

  def testSCL7895(): Unit = doTest {
    """
      |object SCL7895 {
      |  import scala.language.existentials
      |
      |  trait F[A] {def f}
      |  def t: Iterable[(F[A],F[A]) forSome {type A}] = ???
      |
      |  def fail = t.foreach{case (f1,f2) =>
      |   def f3 = f1
      |  /*start*/f3/*end*/.f} // doesn't recognize f1 type here
      |}
      |//SCL7895.F[_]
    """.stripMargin.trim
  }

  def testSCL8610(): Unit = doTest {
    """
      |object SCL8610 {
      |trait A[T] {
      |  def foo(x : T => T)
      |}
      |
      |trait B {
      |  def bar(x : A[_]) {
      |    x.foo(/*start*/y => y/*end*/) // here
      |  }
      |}
      |}
      |//(_$1) => _$1
    """.stripMargin.trim
  }

  def testSCL5725(): Unit = doTest {
    """
      |class Zoo {
      |  def g: Any = 1
      |  def test = g match {
      |    case l: List[s] =>
      |      /*start*/l(0)/*end*/
      |  }
      |}
      |//s
    """.stripMargin.trim
  }
}

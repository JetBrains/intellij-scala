package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class CurriedConformanceTest extends TypeConformanceTestBase {

  def testSCL7488(): Unit = {
    doTest(
      s"""
        |import scala.language.higherKinds
        |
        |type F[_]
        |def unit[A](a: => A): F[A]
        |def apply[A,B](fab: F[A => B])(fa: F[A]): F[B]
        |def mapInTermsOfApplyAndUnit[A,B](fa: F[A])(f: A => B) = {
        |    val apply1 = apply(unit(f))(_)
        |    ${caretMarker}val v: F[A] => F[B] = apply1
        |}
        |//True
      """.stripMargin)
  }

  def testSCL8977(): Unit = {
    doTest(
      s"""
        |object Foo {
        |  import scala.reflect.ClassTag
        |
        |  class Bar[A] { def action(f: A => A): Unit = () }
        |  def updater[A: ClassTag](update: A => A)(a: A): A = { update(a) }
        |  def updater2[A: ClassTag](update: A => A): A => A = { update }
        |  def updater3[A](update: A => A)(a: A): A = { update(a) }
        |
        |  val bar = new Bar[Int]()
        |  // Compiles and runs fine but intellij underlines in red:
        |  bar.action(updater(a => a))
        |  // Non-curried function is okay:
        |  bar.action(updater2(a => a))
        |  // Method without ClassTag bounds works okay
        |  bar.action(updater3(a => a))
        |  // Type hint fixes things
        |  bar.action(updater((a: Int) => a))
        |
        |  ${caretMarker}val foo: (Int) => Int = updater(a => a)
        |}
        |//True
      """.stripMargin)
  }

  def testSCL10906(): Unit = {
    doTest(
      s"""
         |import scala.collection.TraversableLike
         |import scala.collection.generic.CanBuildFrom
         |
         |class t2 {
         |
         |  def foo[A, Repr, That](options: TraversableLike[Option[A], Repr],default: Option[A] = Some("".asInstanceOf[A]))(implicit bf: CanBuildFrom[Repr, A, That]): That = ???
         |
         |  ${caretMarker}val ints: Seq[Int] = foo(Seq(Some(1), None))
         |}
         |//True
      """.stripMargin)
  }
}

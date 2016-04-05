package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeConformance.TypeConformanceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class CurriedConformanceTest extends TypeConformanceTestBase {
  def testScl7462(): Unit = {
    doTest(
      """import scala.collection.GenTraversableOnce
        |
        |def f(curry: String)(i: String): Option[String] = Some(i)
        |
        |val x: (String) => GenTraversableOnce[String] = f("curry")
        |//True""".stripMargin)
  }

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

}

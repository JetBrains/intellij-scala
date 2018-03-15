package org.jetbrains.plugins.scala.failed.typeInference

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.lang.typeInference.TypeInferenceTestBase
import org.junit.experimental.categories.Category

/**
  * @author Nikolay.Tropin
  */
@Category(Array(classOf[PerfCycleTests]))
class ForStmtDesugaringTest extends TypeInferenceTestBase {

  override protected def shouldPass: Boolean = false

  def testSCL8580(): Unit = {
    doTest(
      s"""case class Filterable(s: List[String]) {
          |  def withFilter(p: List[String] => Boolean) = Monadic(s)
          |}
          |
          |case class Monadic(s: List[String]) {
          |  def map(f: List[String] => List[String]): Monadic = Monadic(f(s))
          |  def flatMap(f: List[String] => Monadic): Monadic = f(s)
          |  def foreach(f: List[String] => Unit): Unit = f(s)
          |  def withFilter(q: List[String] => Boolean): Monadic = this
          |}
          |
          |val filterable = Filterable(List("aaa"))
          |
          |${START}for (List(s) <- filterable) yield List(s, s)$END
          |
          |//is desugared to:
          |//                 filterable.map { case List(s) => List(s, s) }
          |//
          |//should be due to irrefutable pattern:
          |//                 filterable.withFilter {case List(s) => true; case _ => false}.map { case List(s) => List(s, s) }
          |
          |//Monadic""".stripMargin)
  }
}

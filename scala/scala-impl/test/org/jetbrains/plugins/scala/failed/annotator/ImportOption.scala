package org.jetbrains.plugins.scala.failed.annotator

import org.jetbrains.plugins.scala.PerfCycleTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.junit.experimental.categories.Category

/**
  * Created by kate on 4/4/16.
  */

@Category(Array(classOf[PerfCycleTests]))
class ImportOption extends ScalaLightCodeInsightFixtureTestAdapter{
  override protected def shouldPass: Boolean = false
  //excluding Some, None from import fix highlighting problems
  //e.g. import scala.{Some => _, None => _, Option => _, Either => _, _}
  def testSCL9818(): Unit = {
    checkTextHasNoErrors(
      """
        |import scala.{Option => _, Either => _, _}
        |
        |sealed trait Option[+A] {
        |  def map[B](f: A => B): Option[B] = this match {
        |    case None => None
        |    case Some(a) => Some(f(a))
        |  }
        |  def getOrElse[B>:A](default: => B): B = this match {
        |    case None => default
        |    case Some(a) => a
        |  }
        |}
        |case class Some[+A](get: A) extends Option[A]
        |case object None extends Option[Nothing]
        |
        |object BugReport extends App {
        |  println("hello " + Some("w").map(_ + "orld").getOrElse("rong"))
        |}
      """.stripMargin)
  }
}

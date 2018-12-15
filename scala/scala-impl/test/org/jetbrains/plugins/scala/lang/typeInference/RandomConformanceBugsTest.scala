package org.jetbrains.plugins.scala.lang.typeInference

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter

class RandomConformanceBugsTest extends ScalaLightCodeInsightFixtureTestAdapter {
  def testSCL9738(): Unit = {
    checkTextHasNoErrors(
      s"""
         |sealed trait FeedbackReason
         |case object CostReason extends FeedbackReason
         |case object BugsReason extends FeedbackReason
         |case object OtherReason extends FeedbackReason
         |
         |object FeedbackTypes {
         |  def asMap(): Map[FeedbackReason, String] = {
         |    val reasons = Map(
         |      CostReason -> "It's too expensive",
         |      BugsReason -> "It's buggy"
         |    )
         |    reasons ++ Map(OtherReason -> "Some other reason")
         |  }
         |}
      """.stripMargin)
  }

  def testSCL14533(): Unit =
    checkTextHasNoErrors(
      """
        |trait Implicit[F[_]]
        |trait Context {
        |  type IO[T]
        |  implicit val impl: Implicit[IO] = new Implicit[IO] {}
        |}
        |class Component[C <: Context](val c: C { type IO[T] = C#IO[T] }) {
        |  import c._
        |  def x(): Unit = {
        |    val a: Implicit[c.IO] = c.impl
        |    val b: Implicit[C#IO] = c.impl
        |  }
        |}
        |
      """.stripMargin)
}

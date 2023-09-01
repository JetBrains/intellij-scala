package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

final class ToggleTypeAnnotationIntentionTest_Scala2_13 extends ToggleTypeAnnotationIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_13

  def testCollectionFactoryNoSimplification(): Unit = doTest(
    "val v = Seq.empty[String].to(Seq)",
    "val v: Seq[String] = Seq.empty[String].to(Seq)"
  )

  override def testAddTypeToMatchPattern(): Unit = doTest(
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag =>
       |  }
       |}
       |""".stripMargin,

    //NOTE: I am not sure if it's expected to have `0` literal type, but it's how it works now (maybe it should be still Int here?)
    s"""
       |object Test {
       |  0 match {
       |    case x$caretTag: 0 =>
       |  }
       |}
       |""".stripMargin
  )
}

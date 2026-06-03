package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

final class ToggleTypeAnnotationIntentionTest_Scala2_12 extends ToggleTypeAnnotationIntentionTestBase {
  override protected def supportedIn(version: ScalaVersion): Boolean = version == LatestScalaVersions.Scala_2_12

  def testCollectionFactoryNoSimplification(): Unit = doTest(
    "val v = Seq.empty[String].to[Seq]",
    "val v: Seq[String] = Seq.empty[String].to[Seq]"
  )

  def testOptionFactoryNoSimplification(): Unit = doTest(
    "val v = Option.empty[String].to[Option]",
    "val v: Option[String] = Option.empty[String].to[Option]"
  )
}

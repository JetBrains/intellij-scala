package org.jetbrains.plugins.scala.codeInsight.intention.types

import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

final class ToggleTypeAnnotationIntentionTest_WithKindProjector extends ScalaIntentionTestBase {
  override def familyName: String = ToggleTypeAnnotation.FamilyName

  override protected def supportedIn(version: ScalaVersion): Boolean = version >= LatestScalaVersions.Scala_2_12

  override def setUp(): Unit = {
    super.setUp()
    val defaultProfile = ScalaCompilerConfiguration.instanceIn(getProject).defaultProfile
    val newSettings = defaultProfile.getSettings.copy(
      plugins = defaultProfile.getSettings.plugins :+ "kind-projector"
    )
    defaultProfile.setSettings(newSettings)
  }

  def testTypeLambdaInline(): Unit = doTest(
    s"""
       |def foo: ({type L[A] = Either[String, A]})#L
       |val ${caretTag}v = foo
     """.stripMargin,
    s"""
       |def foo: ({type L[A] = Either[String, A]})#L
       |val ${caretTag}v: Either[String, ?] = foo
     """.stripMargin
  )

  def testTypeLambda(): Unit = doTest(
    s"""
       |def foo: ({type L[F[_]] = F[Int]})#L
       |val ${caretTag}v = foo
     """.stripMargin,
    s"""
       |def foo: ({type L[F[_]] = F[Int]})#L
       |val ${caretTag}v: Lambda[F[_] => F[Int]] = foo
     """.stripMargin
  )
}

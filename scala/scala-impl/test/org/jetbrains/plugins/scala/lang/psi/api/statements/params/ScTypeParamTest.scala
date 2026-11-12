package org.jetbrains.plugins.scala.lang.psi.api.statements.params

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests capture-parameter classification and declaration metadata exposed by ScTypeParam.
 * Covers mixed parameters, variance, polymorphic owners and feature gating using source PSI.
 * Bound accessors and stub/AST transitions have separate suites.
 */
class ScTypeParamTest extends SimpleTestCase {
  override protected def scalaCodeParsingFeatures: ScalaFeatures = CaptureCheckingTestUtils.parsingFeatures

  def testCaptureMarkerOnMixedParameters(): Unit = {
    val parameters = parametersIn("def f[T, C^, U, D^](x: T): U = ???")
    assertEquals(Seq("T", "C", "U", "D"), parameters.map(_.name))
    assertEquals(Seq(false, true, false, true), parameters.map(_.isCaptureSetParameter))
  }

  def testCaptureParameterNameExcludesCaret(): Unit = {
    for (source <- Seq(
      "def f[C^](x: Object^{C}): Unit = ()",
      "class Box[T >: <: AnyRef, C^]"
    )) {
      val parameter = parametersIn(source).last
      assertEquals(source, "C", parameter.name)
      assertEquals(source, "C^", parameter.getText)
      assertTrue(source, parameter.isCaptureSetParameter)
    }
  }

  def testCaptureMarkerPreservesVariance(): Unit = {
    val parameters = parametersIn("class Box[+C^, -D^, T]")
    assertEquals(Seq(true, true, false), parameters.map(_.isCaptureSetParameter))
    assertEquals(Seq(true, false, false), parameters.map(_.isCovariant))
    assertEquals(Seq(false, true, false), parameters.map(_.isContravariant))
  }

  def testCaptureMarkerInPolymorphicTypes(): Unit = {
    val parameters = parametersIn("type F = [C^] =>> Object^{C}\ntype G = [C^] -> Object^{C}")
    assertEquals(Seq("C", "C"), parameters.map(_.name))
    assertTrue(parameters.forall(_.isCaptureSetParameter))
    assertEquals(Seq(false, false), parameters.map(_.isCovariant))
  }

  def testCaptureMarkerRequiresCaptureChecking(): Unit = {
    val file = parseScalaFile("def f[C^] = ()", ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_8))
    val parameters = file.depthFirst().filterByType[ScTypeParam].toSeq
    assertFalse(parameters.isEmpty)
    assertTrue(parameters.forall(!_.isCaptureSetParameter))
  }

  private def parametersIn(source: String): Seq[ScTypeParam] =
    parseScalaFile(source).depthFirst().filterByType[ScTypeParam].toSeq
}

package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScWildcardTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests the syntactic lower/upper bound accessors and ownership of their returned PSI.
 * Covers parameters, abstract members and wildcards across comments and refinements,
 * keeping context bounds separate from ordinary type bounds without computing types.
 * Also separates capture bounds from type bounds on parameters and abstract/opaque aliases,
 * including incomplete capture bounds, a missing lower bound and feature-disabled refinements.
 */
class ScTypeBoundsOwnerTest extends SimpleTestCase {
  def testBoundsAcrossCommentsAndRefinements(): Unit = {
    for (version <- Seq(ScalaVersion.Latest.Scala_2_13, ScalaVersion.Latest.Scala_3_8)) {
      checkBoundsAcrossCommentsAndRefinements(version)
    }
  }

  private def checkBoundsAcrossCommentsAndRefinements(version: ScalaVersion): Unit = {
    val wildcard = if (version.isScala3) "?" else "_"
    val file = parseScalaFile(
      s"""trait Bounds[T >: /* lower */ Nothing <: // upper
         |  AnyRef] {
         |  type Member >: /* lower */ Nothing <: { def value: Int }
         |  def values: List[$wildcard >: /* lower */ Nothing <: // upper
         |    AnyRef]
         |}
         |""".stripMargin,
      ScalaFeatures.onlyByVersion(version)
    )

    assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)
    val Seq(parameter) = file.depthFirst().filterByType[ScTypeParam].toSeq
    val Seq(member) = file.depthFirst().filterByType[ScTypeAliasDeclaration].toSeq
    val Seq(wildcardType) = file.depthFirst().filterByType[ScWildcardTypeElement].toSeq
    assertTypeBounds(parameter, Some("Nothing"), Some("AnyRef"))
    assertTypeBounds(member, Some("Nothing"), Some("{ def value: Int }"))
    assertTypeBounds(wildcardType, Some("Nothing"), Some("AnyRef"))
  }

  def testContextBoundsDoNotBecomeOrdinaryBounds(): Unit = {
    val file = parseScalaFile("class Bounds[T: Ordering, U <: AnyRef: Ordering]")
    val Seq(t, u) = file.depthFirst().filterByType[ScTypeParam].toSeq
    assertTrue(t.lowerTypeElement.isEmpty)
    assertTrue(t.upperTypeElement.isEmpty)
    assertTrue(u.lowerTypeElement.isEmpty)
    assertEquals(Some("AnyRef"), u.upperTypeElement.map(_.getText))
    for (parameter <- Seq(t, u)) {
      assertEquals(Seq("Ordering"), parameter.contextBounds.map(_.typeElement.getText))
    }
  }

  def testCaptureParameterBounds(): Unit = {
    for (upperBoundText <- Seq("{any}", "{any.except[C1]}")) {
      checkCaptureParameterBounds(upperBoundText)
    }
  }

  def testCaptureParameterBoundsSurviveIncompleteSyntax(): Unit = {
    val Seq(capture, _) = captureParameters("class Box[C^ <: {any, +T]")
    assertTrue(capture.depthFirst().filterByType[PsiErrorElement].nonEmpty)
    assertTypeBounds(capture, None, None)
    assertCaptureBounds(capture, None, Some("{any"))
  }

  private def checkCaptureParameterBounds(upperBoundText: String): Unit = {
    val Seq(capture, ordinary) = captureParameters(s"class Box[C^ >: {} <: $upperBoundText, T]")
    assertTypeBounds(capture, None, None)
    assertCaptureBounds(capture, Some("{}"), Some(upperBoundText))
    assertCaptureBounds(ordinary, None, None)
  }

  def testMixedTypeAndCaptureParameterBounds(): Unit = {
    val Seq(c, t, d) = captureParameters(
      "class Box[C^ >: {} <: caps.CapSet, T >: Nothing <: { def value: Int }, D^ >: caps.CapSet <: {any}]")
    assertTypeBounds(c, None, Some("caps.CapSet"))
    assertCaptureBounds(c, Some("{}"), None)
    assertTypeBounds(t, Some("Nothing"), Some("{ def value: Int }"))
    assertCaptureBounds(t, None, None)
    assertTypeBounds(d, Some("caps.CapSet"), None)
    assertCaptureBounds(d, None, Some("{any}"))
  }

  def testMissingLowerBoundDoesNotBorrowUpperBound(): Unit = {
    val Seq(t, _) = captureParameters("class Box[T >: <: AnyRef, C^]")
    assertTypeBounds(t, None, Some("AnyRef"))
    assertCaptureBounds(t, None, None)
  }

  def testEmptyRefinementBoundWithoutCaptureChecking(): Unit = {
    val file = parseScalaFile("class Box[T <: {}]", ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_8))
    val Seq(t) = file.depthFirst().filterByType[ScTypeParam].toSeq
    assertTypeBounds(t, None, Some("{}"))
    assertCaptureBounds(t, None, None)
  }

  private def captureParameters(source: String): Seq[ScTypeParam] =
    parseScalaFile(source, CaptureCheckingTestUtils.parsingFeatures)
      .depthFirst().filterByType[ScTypeParam].toSeq

  def testAbstractAliasCaptureBounds(): Unit = assertAliasCaptureBounds(opaque = false)

  def testOpaqueAliasCaptureBounds(): Unit = assertAliasCaptureBounds(opaque = true)

  private def assertAliasCaptureBounds(opaque: Boolean): Unit = {
    val prefix = if (opaque) "opaque type" else "type"
    val rhs = if (opaque) " = caps.CapSet" else ""
    // The Both member follows Scala 3.10.1 tests/pos-custom-args/captures/cap-paramlists4.scala.
    // Mixed bounds also exercise the PSI contract while capture-set semantics are deferred.
    val file = parseScalaFile(
      s"""trait Box[U^, V^, W^] {
         |  $prefix Lower >: /* capture */ {} <: caps.CapSet$rhs
         |  $prefix Upper >: caps.CapSet <: /* capture */ {any}$rhs
         |  $prefix Both >: {V,W} <: {U}$rhs
         |}
         |""".stripMargin,
      CaptureCheckingTestUtils.parsingFeatures
    )
    assertTrue(file.depthFirst().filterByType[PsiErrorElement].isEmpty)
    val aliases = file.depthFirst().filterByType[ScTypeAlias].toSeq
    assertEquals(Seq("Lower", "Upper", "Both"), aliases.map(_.name))
    val Seq(lower, upper, both) = aliases

    assertTypeBounds(lower, None, Some("caps.CapSet"))
    assertTypeBounds(upper, Some("caps.CapSet"), None)
    assertTypeBounds(both, None, None)

    assertCaptureBounds(lower, Some("{}"), None)
    assertCaptureBounds(upper, None, Some("{any}"))
    assertCaptureBounds(both, Some("{V,W}"), Some("{U}"))

    if (opaque) {
      for (alias <- aliases) {
        val definition = alias.asInstanceOf[ScTypeAliasDefinition]
        assertTrue(definition.isOpaque)
        assertEquals(Some("caps.CapSet"), definition.aliasedTypeElement.map(_.getText))
      }
    }
  }

  private def assertTypeBounds(owner: ScTypeBoundsOwner, lower: Option[String], upper: Option[String]): Unit = {
    val lowerElement = owner.lowerTypeElement
    val upperElement = owner.upperTypeElement
    assertEquals(lower, lowerElement.map(_.getText))
    assertEquals(upper, upperElement.map(_.getText))
    for (bound <- lowerElement ++ upperElement) {
      assertSame(owner, bound.getParent)
    }
  }

  private def assertCaptureBounds(owner: ScTypeBoundsOwner, lower: Option[String], upper: Option[String]): Unit = {
    val lowerSet = owner.lowerCaptureSet
    val upperSet = owner.upperCaptureSet
    assertEquals(lower, lowerSet.map(_.getText))
    assertEquals(upper, upperSet.map(_.getText))
    for (bound <- lowerSet ++ upperSet) {
      assertSame(owner, bound.getParent)
    }
  }
}

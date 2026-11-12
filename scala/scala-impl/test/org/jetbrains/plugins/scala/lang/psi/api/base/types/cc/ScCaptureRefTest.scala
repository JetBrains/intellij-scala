package org.jetbrains.plugins.scala.lang.psi.api.base.types.cc

import com.intellij.psi.PsiErrorElement
import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests the reference, reach/read-only flags and optional filter exposed by ScCaptureRef.
 * Exercises filtered and ordinary captures, including incomplete-filter reference access.
 * Bare this is a ScThisReference rather than an ScReference, so captureRef is empty for it.
 * The singular captureFilter accessor retains its existing last-filter behavior and
 * keeps each capture isolated from filters on neighboring captures.
 */
class ScCaptureRefTest extends SimpleTestCase {
  override protected def scalaCodeParsingFeatures: ScalaFeatures = CaptureCheckingTestUtils.parsingFeatures

  def testUnderlyingReferenceExcludesSuffixes(): Unit = {
    for ((source, expected) <- Seq(
      ("this.c.only[p.C].except[p.D].rd", Some("this.c")),
      ("c*.only[p.C]", Some("c")),
      ("this.only[UserTable]", None),
      ("this.except[TransactionTable]", None),
      ("only", Some("only")), ("except", Some("except")),
      ("c.`only`", Some("c.`only`")), ("c.`except`", Some("c.`except`"))
    )) {
      assertEquals(source, expected, capture(source).captureRef.map(_.getText))
    }
  }

  def testUnderlyingReferenceSurvivesIncompleteFilter(): Unit = {
    val incomplete = capture("c.only[")
    assertTrue(incomplete.depthFirst().filterByType[PsiErrorElement].nonEmpty)
    assertEquals(Some("c"), incomplete.captureRef.map(_.getText))
  }

  def testReachFlagWithAndWithoutFilters(): Unit = {
    for ((source, expected) <- Seq(
      ("c*", true), ("c", false),
      ("c*.only[p.C]", true), ("c*.as[C].rd", true),
      ("c.except[Control].rd", false), ("any.only[Unscoped].rd", false),
      ("any.except[ErrHandler]", false), ("any.only[Shared]", false)
    )) {
      assertEquals(source, expected, capture(source).hasCapabilityReach)
    }
  }

  def testReadOnlyFlagWithAndWithoutFilters(): Unit = {
    for ((source, expected) <- Seq(
      ("c.rd", true), ("c", false),
      ("c.except[Control].rd", true), ("any.only[Unscoped].rd", true),
      ("this.c.only[p.C].except[p.D].rd", true), ("c.only[C]", false)
    )) {
      assertEquals(source, expected, capture(source).isReadOnlyCapability)
    }
  }

  def testOptionalFilterSelectsLastFilter(): Unit = {
    for ((source, expected) <- Seq(
      ("d", None),
      ("c.only[C]", Some(".only[C]")),
      ("c.only[C].except[D].except[E].rd", Some(".except[E]"))
    )) {
      assertEquals(source, expected, capture(source).captureFilter.map(_.getText))
    }
  }

  def testReferenceAndFilterAreLocalToCaptureReference(): Unit = {
    val Seq(filtered, unfiltered) = parseScalaFile("type T = Object^{c.except[Any], d}")
      .depthFirst().filterByType[ScCaptureRef].toSeq
    assertEquals(Some("c"), filtered.captureRef.map(_.getText))
    assertEquals(Some("d"), unfiltered.captureRef.map(_.getText))
    assertEquals(Some(".except[Any]"), filtered.captureFilter.map(_.getText))
    assertTrue(unfiltered.captureFilter.isEmpty)
  }

  private def capture(source: String): ScCaptureRef = s"type T = Object^{$source}".parse[ScCaptureRef]
}

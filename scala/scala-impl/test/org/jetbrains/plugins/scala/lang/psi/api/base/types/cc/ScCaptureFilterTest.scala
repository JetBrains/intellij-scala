package org.jetbrains.plugins.scala.lang.psi.api.base.types.cc

import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, SimpleTestCase}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests filterId for simple, qualified and chained capture filters.
 * Verifies each filter exposes its own reference; syntax/recovery trees live in parser tests.
 */
class ScCaptureFilterTest extends SimpleTestCase {
  override protected def scalaCodeParsingFeatures: ScalaFeatures = CaptureCheckingTestUtils.parsingFeatures

  def testSimpleFilterIdentifiersInSourceOrder(): Unit = {
    assertEquals(Seq("Control"), filterIdentifiers("f.only[Control]"))
    assertEquals(Seq("SharedCapability", "Control", "IOCap"),
      filterIdentifiers("c.only[SharedCapability].except[Control].except[IOCap]"))
  }

  def testQualifiedFilterIdentifiers(): Unit = {
    assertEquals(Seq("p.C", "p.D"), filterIdentifiers("this.c.only[p.C].except[p.D].rd"))
    assertEquals(Seq("p.C"), filterIdentifiers("c*.only[p.C]"))
  }

  private def filterIdentifiers(source: String): Seq[String] =
    parseScalaFile(s"type T = Object^{$source}").depthFirst().filterByType[ScCaptureFilter].map(_.filterId.getText).toSeq
}

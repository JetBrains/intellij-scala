package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.base.{CaptureCheckingTestUtils, SimpleTestCase}
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert._

/**
 * Tests the underlying type and optional capture set exposed by ScCaptureTypeElement.
 * Includes capture types nested in type arguments; filter internals have their own API tests.
 */
class ScCaptureTypeElementTest extends SimpleTestCase {
  override protected def scalaCodeParsingFeatures: ScalaFeatures = CaptureCheckingTestUtils.parsingFeatures

  def testInnerElementInsideTypeArgument(): Unit = {
    val outer = "type T = Option[Failure^{f.only[Control]}]".parse[ScParameterizedTypeElement]
    assertEquals("Option", outer.typeElement.getText)
    assertEquals("[Failure^{f.only[Control]}]", outer.typeArgList.getText)

    val Seq(argument) = outer.typeArgList.typeArguments
    val captureType = argument.typeElement.get.asInstanceOf[ScCaptureTypeElement]
    assertEquals("Failure^{f.only[Control]}", captureType.getText)
    assertEquals("Failure", captureType.innerElement.getText)
    assertEquals(Some("{f.only[Control]}"), captureType.captureSet.map(_.getText))
  }

  def testOptionalCaptureSet(): Unit = {
    val emptySet = "type T = Object^{}".parse[ScCaptureTypeElement]
    assertEquals(Some("{}"), emptySet.captureSet.map(_.getText))
    assertTrue("type T = Object^".parse[ScCaptureTypeElement].captureSet.isEmpty)
  }
}

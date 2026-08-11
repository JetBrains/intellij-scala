package org.jetbrains.plugins.scala.lang.parser.scala3

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.SimpleScalaParserTestBase
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.junit.Assert.assertTrue

/**
 * `into` is a preview feature in Scala 3.7 and 3.8 and is stabilized in 3.9
 */
abstract class IntoModifierParserTestBase extends SimpleScalaParserTestBase {

  protected def intoModifierIsSupported: Boolean

  private def isParsedAsIntoModifier(code: String): Boolean =
    parseText(code).depthFirst().exists(_.getNode.getElementType == ScalaTokenType.IntoKeyword)

  private def checkIntoModifier(code: String): Unit = {
    val message: String =
      if (intoModifierIsSupported) s"'into' in `$code` was not parsed as a modifier"
      else s"'into' in `$code` was unexpectedly parsed as a modifier"
    assertTrue(message, isParsedAsIntoModifier(code) == intoModifierIsSupported)
  }

  def testIntoClass(): Unit = checkIntoModifier("into class A")

  def testIntoTrait(): Unit = checkIntoModifier("into trait A")

  def testIntoEnum(): Unit = checkIntoModifier("into enum A { case B }")

  def testIntoOpaqueTypeAlias(): Unit = checkIntoModifier("into opaque type A = Int")
}

class IntoModifierParserTest_Scala_3_8 extends IntoModifierParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures =
    ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_8)

  override protected def intoModifierIsSupported: Boolean = false
}

class IntoModifierParserTest_Scala_3_8_Preview extends IntoModifierParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures =
    ScalaFeatures.custom(ScalaVersion.Latest.Scala_3_8, hasPreviewFlag = true)

  override protected def intoModifierIsSupported: Boolean = true
}

class IntoModifierParserTest_Scala_3_9 extends IntoModifierParserTestBase {
  override def scalaCodeParsingFeatures: ScalaFeatures =
    ScalaFeatures.onlyByVersion(ScalaVersion.Latest.Scala_3_9)

  override protected def intoModifierIsSupported: Boolean = true
}

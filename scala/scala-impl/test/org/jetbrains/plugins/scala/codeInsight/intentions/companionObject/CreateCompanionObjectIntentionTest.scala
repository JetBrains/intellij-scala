package org.jetbrains.plugins.scala.codeInsight.intentions.companionObject

import com.intellij.testFramework.TestIndexingModeSupporter.IndexingMode
import org.jetbrains.plugins.scala.codeInsight.intentions.ScalaIntentionTestBase
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.util.runners.WithIndexingMode
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaVersion}

/**
  * mattfowler
  * 5/21/2016
  */
@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class CreateCompanionObjectIntentionTest extends ScalaIntentionTestBase {
  override val familyName = ScalaBundle.message("family.name.create.companion.object")

  def testShouldCreateCompanion(): Unit = {
    val text =
      s"""
         |class B${CARET}ar {}
       """.stripMargin

    val expected =
      s"""
         |class Bar {}
         |
         |object Bar {
         |$CARET
         |}
       """.stripMargin

    doTest(text, expected)
  }

  def testShouldCreateCompanionForCaseClass(): Unit = {
    val text =
      s"""
         |case class B${CARET}ar()
       """.stripMargin

    val expected =
      s"""
         |case class Bar()
         |
         |object Bar {
         |$CARET
         |}
       """.stripMargin

    doTest(text, expected)
  }

  def testShouldCreateCompanionForTrait(): Unit = {
    val text =
      s"""
         |trait B${CARET}ar {}
       """.stripMargin

    val expected =
      s"""
         |trait Bar {}
         |
         |object Bar {
         |$CARET
         |}
       """.stripMargin

    doTest(text, expected)
  }


  def testShouldNotShowIfAlreadyHasCompanion(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |class F${CARET}oo { }
         |
         |object Foo { }
       """.stripMargin)

  def testShouldNotShowIfAlreadyHasCompanionAbove(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |object Foo { }
         |class F${CARET}oo { }
       """.stripMargin)

  def testShouldNotShowOnCompanion(): Unit =
    checkIntentionIsNotAvailable(
      s"""
         |class Foo { }
         |
         |object F${CARET}oo { }
       """.stripMargin)
}

@WithIndexingMode(mode = IndexingMode.DUMB_EMPTY_INDEX)
class CreateCompanionObjectIntentionTest_3_Latest extends ScalaIntentionTestBase {
  override def supportedIn(version: ScalaVersion): Boolean = version == ScalaVersion.Latest.Scala_3

  override val familyName = ScalaBundle.message("family.name.create.companion.object")

  private def doTest(text: String, expected: String, useIndentationBasedSyntax: Boolean): Unit = {
    val settings = ScalaCodeStyleSettings.getInstance(getProject)
    val oldSetting = settings.USE_SCALA3_INDENTATION_BASED_SYNTAX
    try {
      settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = useIndentationBasedSyntax
      doTest(text, expected)
    } finally settings.USE_SCALA3_INDENTATION_BASED_SYNTAX = oldSetting
  }

  def testShouldCreateCompanionForClassBraced(): Unit = {
    val text =
      s"""class B${CARET}ar
       """.stripMargin

    val expected =
      s"""class Bar
         |
         |object Bar {
         |$CARET
         |}
       """.stripMargin

    doTest(text, expected, useIndentationBasedSyntax = false)
  }

  def testShouldCreateCompanionForClassBraceless(): Unit = {
    val text =
      s"""class B${CARET}ar
       """.stripMargin

    val expected =
      s"""class Bar
         |
         |object Bar:
         |$CARET
         |end Bar
       """.stripMargin

    doTest(text, expected, useIndentationBasedSyntax = true)
  }

  def testShouldCreateCompanionForTraitBraced(): Unit = {
    val text =
      s"""trait B${CARET}ar
       """.stripMargin

    val expected =
      s"""trait Bar
         |
         |object Bar {
         |$CARET
         |}
       """.stripMargin

    doTest(text, expected, useIndentationBasedSyntax = false)
  }

  def testShouldCreateCompanionForTraitBraceless(): Unit = {
    val text =
      s"""trait B${CARET}ar
       """.stripMargin

    val expected =
      s"""trait Bar
         |
         |object Bar:
         |$CARET
         |end Bar
       """.stripMargin

    doTest(text, expected, useIndentationBasedSyntax = true)
  }

  def testShouldCreateCompanionForEnumBraced(): Unit = {
    val text =
      s"""enum B${CARET}ar:
         |  case Baz
       """.stripMargin

    val expected =
      s"""enum Bar:
         |  case Baz
         |
         |object Bar {
         |$CARET
         |}
       """.stripMargin

    doTest(text, expected, useIndentationBasedSyntax = false)
  }

  def testShouldCreateCompanionForEnumBraceless(): Unit = {
    val text =
      s"""enum B${CARET}ar:
         |  case Baz
       """.stripMargin

    val expected =
      s"""enum Bar:
         |  case Baz
         |
         |object Bar:
         |$CARET
         |end Bar
       """.stripMargin

    doTest(text, expected, useIndentationBasedSyntax = true)
  }
}

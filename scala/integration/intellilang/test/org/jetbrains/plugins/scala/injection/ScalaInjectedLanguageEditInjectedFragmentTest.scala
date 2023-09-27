package org.jetbrains.plugins.scala.injection

import com.intellij.codeInsight.intention.impl.QuickEditAction
import com.intellij.testFramework.fixtures.InjectionTestFixture
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.StringExt
import org.junit.Assert.assertEquals

/**
 * Tests for editing injected code fragment via:<br>
 * [[com.intellij.codeInsight.intention.impl.QuickEditAction]]<br>
 * (e.g. using "Edit RegExp fragment")
 */
class ScalaInjectedLanguageEditInjectedFragmentTest extends ScalaLightCodeInsightFixtureTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13

  override protected def setUp(): Unit = {
    super.setUp()

    //If we don't disable "caresAboutInjection" flag the original scala file which contains string literal will be lost
    //CodeInsightTestFixtureImpl.setupEditorForInjectedLanguage will be called and override `myFile` with the injected file
    //As an alternative we manually setup editor for the injected fragment in `doTypingTest`
    this.myFixture.setCaresAboutInjection(false)
  }

  private def doTypingTest(
    textToType: String,
    fileTextBefore: String,
    fileTextAfter: String,
    expectedInjectedTextBefore: String
  ): Unit = {
    configureFromFileText(fileTextBefore)

    val quickEditHandler = new QuickEditAction().invokeImpl(getProject, getEditor, getFile)

    val injectedFile = quickEditHandler.getNewFile
    val injectedVirtualFile = injectedFile.getVirtualFile

    assertEquals(
      "Wrong text in injected fragment",
      expectedInjectedTextBefore.withNormalizedSeparator,
      injectedFile.getText
    )

    val injectionTestFixture = new InjectionTestFixture(myFixture)
    val injectedEditorTestFixture = injectionTestFixture.openInFragmentEditor()
    injectedEditorTestFixture.`type`(textToType)

    myFixture.checkResult(fileTextAfter.withNormalizedSeparator)
  }


  private val CommonExpectedInjectedText = """text \w\s"""
  private val CommonTextToType = raw""" text2 \d \s \w """
  private val qqq = "\"\"\""

  def testTypeSpaces_SimpleString(): Unit = doTypingTest(
    CommonTextToType,
    raw""""text \\w\\s$CARET".r""",
    raw""""text \\w\\s text2 \\d \\s \\w ".r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_InterpolatedString(): Unit = doTypingTest(
    CommonTextToType,
    raw"""s"text \\w\\s$CARET".r""",
    raw"""s"text \\w\\s text2 \\d \\s \\w ".r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_InterpolatedString_Raw(): Unit = doTypingTest(
    CommonTextToType,
    raw"""raw"text \w\s$CARET".r""",
    raw"""raw"text \w\s text2 \d \s \w ".r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_MultilineString(): Unit = doTypingTest(
    CommonTextToType,
    raw"""${qqq}text \w\s$CARET$qqq.r""",
    raw"""${qqq}text \w\s text2 \d \s \w $qqq.r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_MultilineInterpolatedString(): Unit = doTypingTest(
    CommonTextToType,
    raw"""s${qqq}text \\w\\s$CARET$qqq.r""",
    raw"""s${qqq}text \\w\\s text2 \\d \\s \\w $qqq.r""",
    CommonExpectedInjectedText
  )

  def testTypeSpaces_MultilineInterpolatedString_Raw(): Unit = doTypingTest(
    CommonTextToType,
    raw"""raw${qqq}text \w\s$CARET$qqq.r""",
    raw"""raw${qqq}text \w\s text2 \d \s \w $qqq.r""",
    CommonExpectedInjectedText
  )

  //SCL-15461
  def testTypeEnter_SimpleString(): Unit = doTypingTest(
    "\n",
    raw"""//language=JAVA
         |"class A {$CARET}"""".stripMargin,
    raw"""//language=JAVA
         |"class A {\n    \n}"""".stripMargin,
    "class A {}"
  )

  //SCL-15461
  def testTypeEnter_InterpolatedString(): Unit = doTypingTest(
    "\n",
    raw"""//language=JAVA
         |s"class A {$CARET}"""".stripMargin,
    raw"""//language=JAVA
         |s"class A {\n    \n}"""".stripMargin,
    "class A {}"
  )

  def testTypeDollar_InSimpleString(): Unit = doTypingTest(
    "$",
    s""""start $CARET end".r""".stripMargin,
    s""""start $$ end".r""".stripMargin,
    "start  end"
  )

  def testTypeDollar_InMultilineString(): Unit = doTypingTest(
    "$",
    s"""${qqq}start $CARET end$qqq.r""".stripMargin,
    s"""${qqq}start $$ end$qqq.r""".stripMargin,
    "start  end"
  )

  def testTypeDollar_InInterpolatedString(): Unit = doTypingTest(
    "$",
    s"""s"start $CARET end".r""".stripMargin,
    s"""s"start $$$$ end".r""".stripMargin,
    "start  end"
  )

  def testTypeDollar_InInterpolatedString_WithExistingDollars(): Unit = doTypingTest(
    "$",
    s"""s"start $$value $CARET $$value end".r""".stripMargin,
    s"""s"start $$value $$$$ $$value end".r""".stripMargin,
    "start InjectionPlaceholder  InjectionPlaceholder end"
  )

  def testTypeDollar_InMultilineInterpolatedString(): Unit = doTypingTest(
    "$",
    s"""s${qqq}start $CARET end$qqq.r""".stripMargin,
    s"""s${qqq}start $$$$ end$qqq.r""".stripMargin,
    "start  end"
  )
}
package org.jetbrains.sbt.lang.completion

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaCompletionAutoPopupTestCase
import org.jetbrains.sbt.language.SbtFileType
import org.junit.Assert.assertNull

import scala.jdk.CollectionConverters._

class SbtAutoPopupInScalacOptionsStringsTest extends ScalaCompletionAutoPopupTestCase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  override def setUp(): Unit = {
    super.setUp()
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  override protected def fileType: FileType = SbtFileType

  private def doTest(textToType: String, expectedLookupItems: Seq[String])(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    val actualLookupItems = myFixture.getLookupElementStrings

    UsefulTestCase.assertContainsElements[String](actualLookupItems, expectedLookupItems.asJava)
  }

  private def doTestNoAutoCompletion(textToType: String)(src: String): Unit = {
    configureByText(src)
    doType(textToType)

    assertNull("Lookup shouldn't be shown", getLookup)
  }

  /// SCALAC OPTIONS

  private val CLASS_STR_TO_TYPE = "class"
  private val CLASS_CONTAINING_FLAGS = Seq("-bootclasspath", "-classpath", "-Ydump-classes")

  def testAutoPopupInScalacOptionsString_OnQuote(): Unit = doTest("\"", CLASS_CONTAINING_FLAGS) {
    s"""scalacOptions += $CARET
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_OnFirstDash(): Unit = doTest("-", CLASS_CONTAINING_FLAGS) {
    s"""scalacOptions += "$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_OnSecondDash(): Unit = doTest("-", CLASS_CONTAINING_FLAGS) {
    s"""scalacOptions += "-$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_Negative_OnThirdDash(): Unit = doTestNoAutoCompletion("-") {
    s"""scalacOptions += "--$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_Negative_OnDashAfterSomeText(): Unit = doTestNoAutoCompletion("-") {
    s"""scalacOptions += "hmmm$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_AfterDash(): Unit = doTest(CLASS_STR_TO_TYPE, CLASS_CONTAINING_FLAGS) {
    s"""scalacOptions += "-$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_FromStart(): Unit = doTest(CLASS_STR_TO_TYPE, CLASS_CONTAINING_FLAGS) {
    s"""scalacOptions ++= Seq("-verbose", "$CARET")
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_CaseInsensitive_AfterDash(): Unit = doTest("xpr", Seq("-Xprint")) {
    s"""scalacOptions += "-$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_CaseInsensitive_FromStart(): Unit = doTest("xpr", Seq("-Xprint")) {
    s"""scalacOptions += "$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_FromStart_Negative_NonexistentOption(): Unit = doTestNoAutoCompletion("nonexistent") {
    s"""scalacOptions += "$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_Middle_Negative_AfterSpace(): Unit = doTestNoAutoCompletion(CLASS_STR_TO_TYPE) {
    s"""scalacOptions += "-verbose $CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_Negative_WrongType_Single(): Unit = doTestNoAutoCompletion(CLASS_STR_TO_TYPE) {
    s"""scalacOptions ++= "$CARET""""
  }

  def testAutoPopupInScalacOptionsString_Negative_WrongType_Seq(): Unit = doTestNoAutoCompletion(CLASS_STR_TO_TYPE) {
    s"""scalacOptions += Seq("$CARET")"""
  }

  /// SCALAC OPTION ARGUMENTS

  def testAutoPopupInScalacOptionsString_Args_AfterColon(): Unit = doTest("ex", Seq("experimental.macros", "existentials")) {
    s"""scalacOptions += "-language:$CARET""""
  }

  def testAutoPopupInScalacOptionsString_Args_AfterComma(): Unit = doTest("ex", Seq("experimental.macros")) {
    s"""scalacOptions += "-language:existentials,$CARET""""
  }

  def testAutoPopupInScalacOptionsString_Args_CamelHump(): Unit = doTest("reca", Seq("reflectiveCalls")) {
    s"""scalacOptions += "-language:$CARET""""
  }

  def testAutoPopupInScalacOptionsString_Args_DotIsWordDelimeter(): Unit = doTest("exma", Seq("experimental.macros")) {
    s"""scalacOptions += "-language:existentials,$CARET""""
  }

  def testAutoPopupInScalacOptionsString_Args_Negative_BeforeColon(): Unit = doTestNoAutoCompletion(CLASS_STR_TO_TYPE) {
    s"""scalacOptions += "-language$CARET:""""
  }

  def testAutoPopupInScalacOptionsString_Args_Negative_NotInScalacOptions(): Unit = doTestNoAutoCompletion(CLASS_STR_TO_TYPE) {
    s"""javacOptions += "-language:$CARET""""
  }
}

package org.jetbrains.sbt.lang.completion

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.openapi.fileTypes.FileType
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.jetbrains.sbt.language.SbtFileType
import org.jetbrains.sbt.language.utils.{SbtScalacOptionInfo, SbtScalacOptionUtils}
import org.junit.Assert.assertNull

import scala.jdk.CollectionConverters._

class SbtAutoPopupInScalacOptionsStringsTest extends EditorActionTestBase {
  protected var myTester: CompletionAutoPopupTester = _

  override def setUp(): Unit = {
    super.setUp()
    myTester = new CompletionAutoPopupTester(myFixture)
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  override protected def fileType: FileType = SbtFileType

  override def runInDispatchThread() = false

  private def doTest(textToType: String, expectedLookupItems: Seq[String])(src: String): Unit = {
    myFixture.configureByText(defaultFileName, src)
    myTester.typeWithPauses(textToType)

    val actualLookupItems = myFixture.getLookupElementStrings

    UsefulTestCase.assertContainsElements[String](actualLookupItems, expectedLookupItems.asJava)
  }

  private def doTestNoAutoCompletion(textToType: String)(src: String): Unit = {
    myFixture.configureByText(defaultFileName, src)
    myTester.typeWithPauses(textToType)

    assertNull("Lookup shouldn't be shown", myTester.getLookup)
  }

  def testAutoPopupInScalacOptionsString_AfterDash(): Unit = doTest("class", loadFlags(_.flag.contains("-class"))) {
    s"""scalacOptions += "-$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_FromStart(): Unit = doTest("class", loadFlags(_.flag.contains("class"))) {
    s"""scalacOptions ++= Seq("-verbose", "$CARET")
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_FromStart_Negative_NonexistentOption(): Unit = doTestNoAutoCompletion("nonexistent") {
    s"""scalacOptions += "$CARET"
       |""".stripMargin
  }

  def testAutoPopupInScalacOptionsString_Middle_Negative_AfterSpace(): Unit = doTestNoAutoCompletion("class") {
    s"""scalacOptions += "-verbose $CARET"
       |""".stripMargin
  }

  private def loadFlags(filter: SbtScalacOptionInfo => Boolean): Seq[String] =
    SbtScalacOptionUtils.getScalacOptions
      .filter(_.scalaVersions.contains(version.languageLevel))
      .filter(filter)
      .map(_.flag)
}

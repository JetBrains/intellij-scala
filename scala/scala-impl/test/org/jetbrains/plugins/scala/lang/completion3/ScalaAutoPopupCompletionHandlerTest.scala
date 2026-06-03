package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.base.ScalaCompletionAutoPopupTestCase
import org.junit.Assert.assertNull

import scala.jdk.CollectionConverters._

class ScalaAutoPopupCompletionHandlerTest extends ScalaCompletionAutoPopupTestCase {

  override def setUp(): Unit = {
    super.setUp()
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  private def doTest(textToType: String, expectedLookupItems: String*)(src: String): Unit = {
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

  def testAutoPopupInTypeAnnotation_patternDefinition(): Unit = doTest(":", "Seq[String]") {
    s"""object O {
       |  val v$CARET = Seq.empty[String]
       |}""".stripMargin
  }

  def testAutoPopupInTypeAnnotation_variableDefinition(): Unit = doTest(":", "Seq[String]") {
    s"""object O {
       |  var v$CARET = Seq.empty[String]
       |}""".stripMargin
  }

  def testAutoPopupInTypeAnnotation_functionDefinition(): Unit = doTest(":", "Seq[String]") {
    s"""object O {
       |  def f()$CARET = Seq.empty[String]
       |}""".stripMargin
  }

  def testNoAutoPopupInTypeAnnotation_valueDeclaration(): Unit = doTestNoAutoCompletion(":") {
    s"""object O {
       |  val v$CARET
       |}""".stripMargin
  }

  def testNoAutoPopupInTypeAnnotation_variableDeclaration(): Unit = doTestNoAutoCompletion(":") {
    s"""object O {
       |  var v$CARET
       |}""".stripMargin
  }

  def testNoAutoPopupInTypeAnnotation_functionDeclaration(): Unit = doTestNoAutoCompletion(":") {
    s"""object O {
       |  def f()$CARET
       |}""".stripMargin
  }

}

package org.jetbrains.plugins.scala
package lang.completion3

import com.intellij.codeInsight.editorActions.CompletionAutoPopupHandler
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.testFramework.{TestModeFlags, UsefulTestCase}
import org.jetbrains.plugins.scala.base.EditorActionTestBase
import org.junit.Assert.assertNull
import org.junit.experimental.categories.Category

import scala.jdk.CollectionConverters._

@Category(Array(classOf[LanguageTests]))
class ScalaAutoPopupCompletionHandlerTest extends EditorActionTestBase {
  private[this] var myTester: CompletionAutoPopupTester = _

  override def runInDispatchThread() = false

  override def setUp(): Unit = {
    super.setUp()
    myTester = new CompletionAutoPopupTester(myFixture)
    TestModeFlags.set[java.lang.Boolean](
      CompletionAutoPopupHandler.ourTestingAutopopup, true, getTestRootDisposable
    )
  }

  private def doTest(textToType: String, expectedLookupItems: String*)(src: String): Unit = {
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

  def testAutoPopupInTypeAnnotation_typeOnSelection(): Unit = doTest(":", "Seq[String]") {
    s"""object O {
       |  val v$START: SomeType$END$CARET = Seq.empty[String]
       |}""".stripMargin
  }

  def testNoAutoPopupInTypeAnnotation_typeOnWrongSelection(): Unit = doTestNoAutoCompletion(":") {
    s"""object O {
       |  val v$START: Some$END${CARET}Type = Seq.empty[String]
       |}""".stripMargin
  }

}

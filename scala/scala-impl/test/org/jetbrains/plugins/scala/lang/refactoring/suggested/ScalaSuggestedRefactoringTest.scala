package org.jetbrains.plugins.scala.lang.refactoring.suggested

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.refactoring.RefactoringBundle
import junit.framework.AssertionFailedError
import junit.framework.TestCase.assertEquals
import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.extensions.{executeWriteActionCommand, inWriteAction, startCommand}

import scala.jdk.CollectionConverters.ListHasAsScala

class ScalaSuggestedRefactoringTest extends ScalaLightCodeInsightFixtureTestCase {
  private def suggestedRefactoringIntention(actionName: String): IntentionAction = {
    val intentions = myFixture.getAvailableIntentions.asScala
    intentions.find(_.getFamilyName == "Suggested Refactoring") match {
      case Some(intention) =>
        assertEquals("Action name", actionName, intention.getText)
        intention
      case None => throw new AssertionFailedError("No refactoring available")
    }
  }

  private def checkRename(
    @Language("Scala")
    fileText: String,
    @Language("Scala")
    resultText: String,
    oldName: String,
    textToType: String,
  ): Unit = checkRename(
    fileText = fileText,
    resultText = resultText,
    oldName = oldName,
    newName = oldName + textToType,
    editingActions = Seq(() => myFixture.`type`(textToType))
  )

  private def checkRename(
    @Language("Scala")
    fileText: String,
    @Language("Scala")
    resultText: String,
    oldName: String,
    newName: String,
    editingActions: Seq[() => Unit]
  ): Unit = {
    val actionName = RefactoringBundle.message("suggested.refactoring.rename.intention.text", oldName, newName)
    doTest(actionName, fileText, resultText, editingActions)
  }

  private def doTest(
    actionName: String,
    fileText: String,
    resultText: String,
    editingActions: Seq[() => Unit]
  ): Unit = {
    configureFromFileText(fileText)
    myFixture.testHighlighting(false, false, false, getFile.getVirtualFile)

    implicit val project: Project = getProject
    executeEditingActions(editingActions)

    val intention = suggestedRefactoringIntention(actionName)

    startCommand() {
      intention.invoke(project, getEditor, getFile)

      inWriteAction {
        PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
      }
    }

    scalaFixture.checkResultByText(resultText)

    myFixture.testHighlighting(false, false, false, getFile.getVirtualFile)
  }

  private def executeEditingActions(actions: Seq[() => Unit], wrapAndCommit: Boolean = true)
                                   (implicit project: Project): Unit = {
    val manager = PsiDocumentManager.getInstance(project)

    actions.foreach { action =>
      if (wrapAndCommit) {
        executeWriteActionCommand() {
          action()
          manager.commitAllDocuments()
          manager.doPostponedOperationsAndUnblockDocument(getEditor.getDocument)
        }
      } else action()
    }

    manager.commitAllDocuments()
  }

  def testRenameClass(): Unit = checkRename(
    fileText =
      s"""
         |class C {
         |  private class Inner$CARET {}
         |
         |  private def foo(inner: Inner): Unit = {
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class C {
         |  private class InnerNew$CARET {}
         |
         |  private def foo(inner: InnerNew): Unit = {
         |  }
         |}
         |""".stripMargin,
    oldName = "Inner",
    textToType = "New",
  )

  def testRenameClass_endOfLine(): Unit = checkRename(
    fileText =
      s"""
         |class C {
         |  private class Inner$CARET
         |
         |  private def foo(inner: Inner): Unit = {
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class C {
         |  private class InnerNew$CARET
         |
         |  private def foo(inner: InnerNew): Unit = {
         |  }
         |}
         |""".stripMargin,
    oldName = "Inner",
    textToType = "New",
  )

  def testRenameClass_deleteChars(): Unit = checkRename(
    fileText =
      s"""
         |class Class${CARET}ss
         |
         |class C extends Classss
         |""".stripMargin,
    resultText =
      s"""
         |class Cla${CARET}ss
         |
         |class C extends Class
         |""".stripMargin,
    oldName = "Classss",
    newName = "Class",
    editingActions = Seq.fill(2)(() => myFixture.performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE))
  )

  def testRenameClass_selectAndType(): Unit = checkRename(
    fileText =
      s"""
         |class ${CARET}Class
         |
         |class C extends Class
         |""".stripMargin,
    resultText =
      s"""
         |class Base$CARET
         |
         |class C extends Base
         |""".stripMargin,
    oldName = "Class",
    newName = "Base",
    editingActions = Seq(
      () => {
        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_SELECT_WORD_AT_CARET)
        myFixture.`type`("Base")
      }
    )
  )

  def testRenameMethod(): Unit = checkRename(
    fileText =
      s"""
         |class C {
         |  def foo$CARET(i: Int): Unit = println(i)
         |
         |  def baz(): Unit = {
         |    foo(1)
         |    println()
         |    foo(2)
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class C {
         |  def fooBar$CARET(i: Int): Unit = println(i)
         |
         |  def baz(): Unit = {
         |    fooBar(1)
         |    println()
         |    fooBar(2)
         |  }
         |}
         |""".stripMargin,
    oldName = "foo",
    textToType = "Bar",
  )

  def testRenameLocalVariableInPattern(): Unit = checkRename(
    fileText =
      s"""
         |class C {
         |  def test(): Unit = {
         |    val Array(_, (b, s$CARET)) = Array((true, "foo"), (false, "bar"))
         |    println(s)
         |  }
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class C {
         |  def test(): Unit = {
         |    val Array(_, (b, str$CARET)) = Array((true, "foo"), (false, "bar"))
         |    println(str)
         |  }
         |}
         |""".stripMargin,
    oldName = "s",
    textToType = "tr",
  )

  def testRenameConstructorParam(): Unit = checkRename(
    fileText =
      s"""
         |class C(val ${CARET}t: Int) {
         |  def foo: Int = t + 2
         |}
         |
         |object Test {
         |  def test(): Unit = new C(t = 3)
         |}
         |""".stripMargin,
    resultText =
      s"""
         |class C(val in${CARET}t: Int) {
         |  def foo: Int = int + 2
         |}
         |
         |object Test {
         |  def test(): Unit = new C(int = 3)
         |}
         |""".stripMargin,
    oldName = "t",
    newName = "int",
    editingActions = Seq(() => myFixture.`type`("in")),
  )
}

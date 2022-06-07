package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.intention.IntentionAction
import org.jetbrains.plugins.scala.extensions.{StringExt, executeWriteActionCommand}
import org.junit.Assert.{assertFalse, assertTrue, fail}

import scala.jdk.CollectionConverters.CollectionHasAsScala

abstract class ScalaAnnotatorQuickFixTestBase extends ScalaHighlightsTestBase {

  import ScalaAnnotatorQuickFixTestBase.quickFixes

  protected def testQuickFix(text: String, expected: String, hint: String): Unit = {
    val action = doFindQuickFix(text, hint)

    executeWriteActionCommand() {
      action.invoke(getProject, getEditor, getFile)
    }(getProject)

    val expectedFileText = createTestText(expected)
    myFixture.checkResult(expectedFileText.withNormalizedSeparator.trim,true)
  }

  protected def testQuickFixAllInFile(text: String, expected: String, hint: String): Unit = {
    val actions = doFindQuickFixes(text, hint)

    executeWriteActionCommand() {
      actions.foreach(_.invoke(getProject, getEditor, getFile))
    }(getProject)

    val expectedFileText = createTestText(expected)
    myFixture.checkResult(expectedFileText.withNormalizedSeparator.trim, true)
  }

  protected def checkNotFixable(text: String, hint: String): Unit = {
    checkNotFixable(text, _ == hint)
  }

  protected def checkNotFixable(text: String, hintFilter: String => Boolean): Unit = {
    val maybeAction = findQuickFix(text, hintFilter, failOnEmptyErrors = false)
    assertTrue("Quick fix found.", maybeAction.isEmpty)
  }

  protected def checkIsNotAvailable(text: String, hint: String): Unit = {
    val action = doFindQuickFix(text, hint)
    assertFalse("Quick fix is available", action.isAvailable(getProject, getEditor, getFile))
  }

  private def findQuickFix(text: String, hintFilter: String => Boolean, failOnEmptyErrors: Boolean = true): Option[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors)
    actions.find(a => hintFilter(a.getText))
  }

  private def doFindQuickFix(text: String, hint: String, failOnEmptyErrors: Boolean = true): IntentionAction =
    doFindQuickFixes(text, hint, failOnEmptyErrors).head

  protected def doFindQuickFixes(text: String, hint: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] = {
    val actions = findAllQuickFixes(text, failOnEmptyErrors)
    val actionsMatching = actions.filter(_.getText == hint)
    assert(actionsMatching.nonEmpty, s"Quick fixes not found. Available actions:\n${actions.map(_.getText).mkString("\n")}")
    actionsMatching
  }

  protected def findAllQuickFixes(text: String, failOnEmptyErrors: Boolean = true): Seq[IntentionAction] =
    configureByText(text).actualHighlights match {
      case Seq() if failOnEmptyErrors => fail("Errors not found.").asInstanceOf[Nothing]
      case seq => seq.flatMap(quickFixes)
    }
}

object ScalaAnnotatorQuickFixTestBase {
  private def quickFixes(info: HighlightInfo): Seq[IntentionAction] = {
    Option(info.quickFixActionRanges).toSeq
      .flatMap(_.asScala)
      .flatMap(pair => Option(pair))
      .map(_.getFirst.getAction)
  }
}

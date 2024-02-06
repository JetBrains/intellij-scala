package org.jetbrains.plugins.scala.lang.completion3.base

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{Lookup, LookupElement, LookupElementPresentation, LookupManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import org.jetbrains.plugins.scala.base.{HelperFixtureEditorOps, ScalaCodeInsightTestFixture}
import org.jetbrains.plugins.scala.extensions.{StringExt, invokeAndWait}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase.{DefaultInvocationCount, createPresentation}
import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestFixture._
import org.junit.Assert._

import scala.jdk.CollectionConverters.CollectionHasAsScala

class ScalaCompletionTestFixture(
  val scalaFixture: ScalaCodeInsightTestFixture,
  val defaultInvocationCount: Int = 1,
) extends HelperFixtureEditorOps {

  override protected def getFixture: JavaCodeInsightTestFixture = scalaFixture.javaFixture
  override protected def getProject: Project = scalaFixture.javaFixture.getProject

  private def getEditor: Editor = scalaFixture.javaFixture.getEditor

  private var beforeCompletionListener: () => Unit =  () => {
    //originally added in commit de6af2ff
    //"fix original position check in BlockModificationCount;
    //assertion added + completion tests change psi to indirectly check it #SCL-15630 fixed"
    changePsiAt(getEditor.getCaretModel.getOffset)
  }
  def setCustomBeforeCompletionListener(body: () => Unit): Unit = {
    beforeCompletionListener = body
  }

  final def doCompletionTest(
    fileText: String,
    resultText: String,
    item: String,
    char: Char = Lookup.REPLACE_SELECT_CHAR,
    invocationCount: Int = defaultInvocationCount,
    completionType: CompletionType = CompletionType.BASIC
  ): Unit =
    doRawCompletionTest(fileText, resultText, char, invocationCount, completionType) {
      hasLookupString(_, item)
    }

  final def doRawCompletionTest(
    fileText: String,
    resultText: String,
    char: Char = Lookup.REPLACE_SELECT_CHAR,
    invocationCount: Int = defaultInvocationCount,
    completionType: CompletionType = CompletionType.BASIC
  )(predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    val (lookup, items) = activeLookupWithItems(fileText, completionType, invocationCount)

    items.find(predicate) match {
      case Some(item) =>
        lookup.finishLookup(char, item)
        checkResultByText(resultText)
      case _ =>
        fail(
          s"""Lookup not found.
             |All lookups:
             |${lookupItemsDebugText(items)}""".stripMargin
        )
    }
  }

  final def checkNoBasicCompletion(
    fileText: String,
    item: String,
    invocationCount: Int = defaultInvocationCount
  ): Unit =
    checkNoCompletion(fileText, invocationCount = invocationCount) {
      hasLookupString(_, item)
    }

  final def checkNoCompletion(
    fileText: String,
    `type`: CompletionType = CompletionType.BASIC,
    invocationCount: Int = defaultInvocationCount
  )(predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    scalaFixture.configureFromFileText(fileText)

    val lookups = scalaFixture.javaFixture.complete(`type`, invocationCount)
    if (lookups != null && lookups.exists(predicate)) {
      fail(
        s"""Expected no lookups matching predicate.
           |All lookups:
           |${lookupItemsDebugText(lookups)}""".stripMargin
      )
    }
    assertFalse(lookups != null && lookups.exists(predicate))
  }

  final def checkNonEmptyCompletionWithKeyAbortion(
    fileText: String,
    resultText: String,
    char: Char,
    invocationCount: Int = defaultInvocationCount,
    completionType: CompletionType = CompletionType.BASIC
  ): Unit = {
    val (_, items) = activeLookupWithItems(fileText, completionType, invocationCount)
    assertTrue(items.nonEmpty)

    scalaFixture.javaFixture.`type`(char)
    checkResultByText(resultText)
  }

  final def checkEmptyCompletionAbortion(
    fileText: String,
    resultText: String,
    char: Char = Lookup.REPLACE_SELECT_CHAR,
    invocationCount: Int = defaultInvocationCount,
    completionType: CompletionType = CompletionType.BASIC
  ): Unit = {
    val (lookup, items) = activeLookupWithItems(fileText, completionType, invocationCount)
    assertTrue(items.nonEmpty)
    lookup.finishLookup(char, null)
    checkResultByText(resultText)
  }

  final def completeBasic(invocationCount: Int): Array[LookupElement] = {
    assertNotEquals("Please use `completeBasic`", 1, invocationCount)

    val lookups = scalaFixture.javaFixture.complete(CompletionType.BASIC, invocationCount)
    assertNotNull(lookups)
    lookups
  }

  final def activeLookupWithItems(
    fileText: String,
    completionType: CompletionType = CompletionType.BASIC,
    invocationCount: Int = DefaultInvocationCount,
    itemsExtractor: LookupImpl => Iterable[LookupElement] = allItems,
  ): (LookupImpl, Iterable[LookupElement]) = {
    scalaFixture.configureFromFileText(fileText)

    beforeCompletionListener()

    invokeAndWait {
      val completionHandler = createSynchronousCompletionHandler(completionType)
      completionHandler.invokeCompletion(getProject, getEditor, invocationCount)
    }

    val activeLookup = LookupManager.getActiveLookup(getEditor)
    activeLookup match {
      case impl: LookupImpl =>
        val items = itemsExtractor(impl)
        (impl, items)
      case _ =>
        throw new AssertionError("Lookups not found")
    }
  }

  final def createSynchronousCompletionHandler(
    completionType: CompletionType = CompletionType.BASIC,
    autopopup: Boolean = false
  ): CodeCompletionHandlerBase = new CodeCompletionHandlerBase(
    completionType,
    /*invokedExplicitly*/ false,
    autopopup,
    /*synchronous*/ true
  )

  final def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    scalaFixture.checkResultByText(expectedFileText, ignoreTrailingSpaces)
}

object ScalaCompletionTestFixture {
  private def hasLookupString(lookup: LookupElement, lookupString: String): Boolean =
    lookup.getLookupString == lookupString

  private def lookupItemsDebugText(items: Iterable[LookupElement]): String =
    items.map(lookupItemDebugText).mkString("\n")

  //TODO: unify with hasItemText and show difference in test error message
  private def lookupItemDebugText(item: LookupElement): String = {
    val presentation = createPresentation(item)
    "ItemText: " + presentation.getItemText +
      ", TailText: " + presentation.getTailText +
      ", TypeText: " + presentation.getTypeText +
      ", Italic: " + presentation.isItemTextItalic +
      ", Bold: " + presentation.isItemTextBold +
      ", TailGrayed: " + isTailGrayed(presentation)
  }

  def isTailGrayed(presentation: LookupElementPresentation): Boolean =
    presentation.getTailFragments.asScala.headOption.exists(_.isGrayed)

  def allItems(impl: LookupImpl) = {
    import scala.jdk.CollectionConverters._
    impl.getItems.asScala
  }
}

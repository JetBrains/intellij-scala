package org.jetbrains.plugins.scala.lang.completion3.base

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{Lookup, LookupElement, LookupElementPresentation, LookupManager}
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.fixtures.TestLookupElementPresentation
import org.jetbrains.plugins.scala.CompletionTests
import org.jetbrains.plugins.scala.base.{HelperFixtureEditorOps, ScalaLightCodeInsightFixtureTestCase}
import org.jetbrains.plugins.scala.extensions.{StringExt, invokeAndWait}
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.Assert._
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

import scala.jdk.CollectionConverters._

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
@Category(Array(classOf[CompletionTests]))
abstract class ScalaCompletionTestBase extends ScalaLightCodeInsightFixtureTestCase with HelperFixtureEditorOps {

  import CompletionType.BASIC
  import Lookup.REPLACE_SELECT_CHAR
  import ScalaCompletionTestBase._

  protected override def setUp(): Unit = {
    super.setUp()
    StatisticsManager.getInstance match {
      case impl: StatisticsManagerImpl => impl.enableStatistics(getTestRootDisposable)
    }
  }

  override def getTestDataPath: String =
    s"${super.getTestDataPath}completion3/"

  protected final def activeLookupWithItems(
    fileText: String,
    completionType: CompletionType = BASIC,
    invocationCount: Int = DefaultInvocationCount,
    itemsExtractor: LookupImpl => Iterable[LookupElement] = allItems,
  ): (LookupImpl, Iterable[LookupElement]) = {
    configureFromFileText(fileText)

    changePsiAt(getEditor.getCaretModel.getOffset)

    invokeAndWait {
      createSynchronousCompletionHandler(completionType)
        .invokeCompletion(getProject, getEditor, invocationCount)
    }

    LookupManager.getActiveLookup(getEditor) match {
      case impl: LookupImpl =>
        val items = itemsExtractor(impl)
        (impl, items)
      case _ =>
        throw new AssertionError("Lookups not found")
    }
  }

  protected final def createSynchronousCompletionHandler(completionType: CompletionType = BASIC,
                                                         autopopup: Boolean = false) =
    new CodeCompletionHandlerBase(
      completionType,
      /*invokedExplicitly*/ false,
      autopopup,
      /*synchronous*/ true
    )

  protected final def doCompletionTest(fileText: String,
                                       resultText: String,
                                       item: String,
                                       char: Char = REPLACE_SELECT_CHAR,
                                       invocationCount: Int = DefaultInvocationCount,
                                       completionType: CompletionType = BASIC): Unit =
    doRawCompletionTest(fileText, resultText, char, invocationCount, completionType) {
      hasLookupString(_, item)
    }

  protected final def doRawCompletionTest(fileText: String,
                                          resultText: String,
                                          char: Char = REPLACE_SELECT_CHAR,
                                          invocationCount: Int = DefaultInvocationCount,
                                          completionType: CompletionType = BASIC)
                                         (predicate: LookupElement => Boolean = Function.const(true)): Unit = {
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

  protected final def checkNoBasicCompletion(fileText: String,
                                             item: String,
                                             invocationCount: Int = DefaultInvocationCount): Unit =
    checkNoCompletion(fileText, invocationCount = invocationCount) {
      hasLookupString(_, item)
    }

  protected final def checkNoCompletion(fileText: String,
                                        `type`: CompletionType = BASIC,
                                        invocationCount: Int = DefaultInvocationCount)
                                       (predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    configureFromFileText(fileText)

    val lookups = myFixture.complete(`type`, invocationCount)
    if (lookups != null && lookups.exists(predicate)) {
      fail(
        s"""Expected no lookups matching predicate.
           |All lookups:
           |${lookupItemsDebugText(lookups)}""".stripMargin
      )
    }
    assertFalse(lookups != null && lookups.exists(predicate))
  }

  protected final def checkNonEmptyCompletionWithKeyAbortion(fileText: String,
                                                             resultText: String,
                                                             char: Char,
                                                             invocationCount: Int = DefaultInvocationCount,
                                                             completionType: CompletionType = BASIC): Unit = {
    val (_, items) = activeLookupWithItems(fileText, completionType, invocationCount)
    assertTrue(items.nonEmpty)

    myFixture.`type`(char)
    checkResultByText(resultText)
  }

  protected final def checkEmptyCompletionAbortion(fileText: String,
                                                   resultText: String,
                                                   char: Char = REPLACE_SELECT_CHAR,
                                                   invocationCount: Int = DefaultInvocationCount,
                                                   completionType: CompletionType = BASIC): Unit = {
    val (lookup, items) = activeLookupWithItems(fileText, completionType, invocationCount)
    assertTrue(items.nonEmpty)
    lookup.finishLookup(char, null)
    checkResultByText(resultText)
  }

  protected final def completeBasic(invocationCount: Int) = {
    assertNotEquals("Please use `completeBasic`", 1, invocationCount)

    val lookups = myFixture.complete(BASIC, invocationCount)
    assertNotNull(lookups)
    lookups
  }

  protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    myFixture.checkResult(expectedFileText.withNormalizedSeparator.trim, ignoreTrailingSpaces)
}

object ScalaCompletionTestBase {

  val DefaultInvocationCount: Int = 1

  object LookupString {

    def unapply(lookup: LookupElement): Some[String] =
      Some(lookup.getLookupString)
  }

  def hasLookupString(lookup: LookupElement, lookupString: String): Boolean =
    lookup.getLookupString == lookupString

  def createPresentation(lookup: LookupElement): LookupElementPresentation =
    TestLookupElementPresentation.renderReal(lookup)

  def hasItemText(lookup: LookupElement,
                  lookupString: String)
                 (itemText: String = lookupString,
                  itemTextItalic: Boolean = false,
                  itemTextBold: Boolean = false,
                  tailText: String = null,
                  typeText: String = null,
                  grayed: Boolean = false): Boolean = lookup match {
    case LookupString(`lookupString`) =>
      val presentation = createPresentation(lookup)
      presentation.getItemText == itemText &&
        presentation.isItemTextItalic == itemTextItalic &&
        presentation.isItemTextBold == itemTextBold &&
        presentation.getTailText == tailText &&
        presentation.getTypeText == typeText &&
        isTailGrayed(presentation) == grayed
    case _ =>
      false
  }

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

  private def isTailGrayed(presentation: LookupElementPresentation): Boolean = {
    presentation.getTailFragments.asScala.headOption.exists(_.isGrayed)
  }

  private def allItems(impl: LookupImpl) = {
    import scala.jdk.CollectionConverters._
    impl.getItems.asScala
  }
}
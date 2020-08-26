package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{Lookup, LookupElement, LookupElementPresentation, LookupManager}
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.extensions.invokeAndWait
import org.junit.Assert._

import scala.jdk.CollectionConverters._

/**
 * @author Alexander Podkhalyuzin
 */
abstract class ScalaCodeInsightTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import CompletionType.BASIC
  import Lookup.REPLACE_SELECT_CHAR
  import ScalaCodeInsightTestBase._

  protected override def setUp(): Unit = {
    super.setUp()
    StatisticsManager.getInstance match {
      case impl: StatisticsManagerImpl => impl.enableStatistics(getTestRootDisposable)
    }
  }

  override def getTestDataPath: String =
    s"${super.getTestDataPath}completion3/"

  protected final def activeLookupWithItems(fileText: String,
                                            completionType: CompletionType = BASIC,
                                            invocationCount: Int = DEFAULT_TIME)
                                           (items: LookupImpl => Iterable[LookupElement] = allItems) = {
    configureFromFileText(normalize(fileText))

    changePsiAt(getEditorOffset)

    invokeAndWait {
      createSynchronousCompletionHandler(completionType)
        .invokeCompletion(getProject, getEditor, invocationCount)
    }

    LookupManager.getActiveLookup(getEditor) match {
      case impl: LookupImpl => (impl, items(impl))
      case _ => throw new AssertionError("Lookups not found")
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
                                       time: Int = DEFAULT_TIME,
                                       completionType: CompletionType = BASIC): Unit =
    doRawCompletionTest(fileText, resultText, char, time, completionType) {
      hasLookupString(_, item)
    }

  protected final def doRawCompletionTest(fileText: String,
                                          resultText: String,
                                          char: Char = REPLACE_SELECT_CHAR,
                                          invocationCount: Int = DEFAULT_TIME,
                                          completionType: CompletionType = BASIC)
                                         (predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    val (lookup, items) = activeLookupWithItems(fileText, completionType, invocationCount)()

    items.find(predicate) match {
      case Some(item) =>
        lookup.finishLookup(char, item)
        checkResultByText(resultText)
      case _ => fail("Lookup not found")
    }
  }

  protected final def checkNoBasicCompletion(fileText: String,
                                             item: String,
                                             invocationCount: Int = DEFAULT_TIME): Unit =
    checkNoCompletion(fileText, invocationCount = invocationCount) {
      hasLookupString(_, item)
    }

  protected final def checkNoCompletion(fileText: String,
                                        `type`: CompletionType = BASIC,
                                        invocationCount: Int = DEFAULT_TIME)
                                       (predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    configureFromFileText(fileText)

    val lookups = getFixture.complete(`type`, invocationCount)
    assertFalse(lookups != null && lookups.exists(predicate))
  }

  protected final def checkNonEmptyCompletionWithKeyAbortion(fileText: String,
                                                             resultText: String,
                                                             char: Char,
                                                             invocationCount: Int = DEFAULT_TIME,
                                                             completionType: CompletionType = BASIC): Unit = {
    val (_, items) = activeLookupWithItems(fileText, completionType, invocationCount)()
    assertTrue(items.nonEmpty)

    getFixture.`type`(char)
    checkResultByText(resultText)
  }

  protected final def checkEmptyCompletionAbortion(fileText: String,
                                                   resultText: String,
                                                   char: Char = REPLACE_SELECT_CHAR,
                                                   invocationCount: Int = DEFAULT_TIME,
                                                   completionType: CompletionType = BASIC): Unit = {
    val (lookup, items) = activeLookupWithItems(fileText, completionType, invocationCount)()
    assertTrue(items.nonEmpty)
    lookup.finishLookup(char, null)
    checkResultByText(resultText)
  }

  protected final def completeBasic(invocationCount: Int) = {
    assertNotEquals("Please use `completeBasic`", 1, invocationCount)

    val lookups = getFixture.complete(BASIC, invocationCount)
    assertNotNull(lookups)
    lookups
  }

  protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    getFixture.checkResult(normalize(expectedFileText), ignoreTrailingSpaces)
}

object ScalaCodeInsightTestBase {

  val DEFAULT_TIME: Int = 1

  object LookupString {

    def unapply(lookup: LookupElement): Some[String] =
      Some(lookup.getLookupString)
  }

  def hasLookupString(lookup: LookupElement, lookupString: String): Boolean =
    lookup.getLookupString == lookupString

  def createPresentation(lookup: LookupElement): LookupElementPresentation = {
    val presentation = new LookupElementPresentation
    lookup.renderElement(presentation)
    presentation
  }

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
    case _ => false
  }

  private def isTailGrayed(presentation: LookupElementPresentation): Boolean = {
    presentation.getTailFragments.asScala.headOption.exists(_.isGrayed)
  }

  private def allItems(impl: LookupImpl) = {
    import scala.jdk.CollectionConverters._
    impl.getItems.asScala
  }
}
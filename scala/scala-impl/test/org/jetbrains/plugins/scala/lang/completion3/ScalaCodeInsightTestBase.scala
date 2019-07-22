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
import org.junit.Assert.{assertEquals, assertFalse, fail}

/**
 * @author Alexander Podkhalyuzin
 */
abstract class ScalaCodeInsightTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import CompletionType.BASIC
  import Lookup.REPLACE_SELECT_CHAR
  import ScalaCodeInsightTestBase._

  import collection.JavaConverters._

  protected override def setUp(): Unit = {
    super.setUp()
    StatisticsManager.getInstance match {
      case impl: StatisticsManagerImpl => impl.enableStatistics(getTestRootDisposable)
    }
  }

  override def getTestDataPath: String =
    s"${super.getTestDataPath}completion3/"

  protected final def activeLookupWithItems(items: LookupImpl => Iterable[LookupElement] = _.getItems.asScala) =
    LookupManager.getActiveLookup(getEditor) match {
      case impl: LookupImpl =>
        (impl, items(impl))
      case _ =>
        throw new AssertionError("Lookups not found")
    }

  protected final def lookupItems = {
    val (_, items) = activeLookupWithItems()
    items
  }

  protected def doCompletionTest(fileText: String,
                                 resultText: String,
                                 item: String,
                                 char: Char = REPLACE_SELECT_CHAR,
                                 time: Int = DEFAULT_TIME,
                                 completionType: CompletionType = BASIC): Unit =
    doCompletionTest(fileText, resultText, char, time, completionType) {
      hasLookupString(_, item)
    }

  protected final def doCompletionTest(fileText: String,
                                       resultText: String,
                                       char: Char,
                                       time: Int,
                                       completionType: CompletionType)
                                      (predicate: LookupElement => Boolean): Unit = {
    configureTest(fileText, completionType, time)

    val (lookup, items) = activeLookupWithItems()
    items.find(predicate) match {
      case Some(item) =>
        lookup.finishLookup(char, item)
        checkResultByText(resultText)
      case _ => fail()
    }
  }

  protected def doMultipleCompletionTest(fileText: String,
                                         count: Int,
                                         item: String,
                                         completionType: CompletionType = BASIC,
                                         time: Int = DEFAULT_TIME): Unit =
    doMultipleCompletionTest(fileText, completionType, time, count) {
      hasLookupString(_, item)
    }

  protected final def doMultipleCompletionTest(fileText: String,
                                               completionType: CompletionType,
                                               time: Int,
                                               count: Int)
                                              (predicate: LookupElement => Boolean): Unit = {
    configureTest(fileText, completionType, time)

    assertEquals(count, lookupItems.count(predicate))
  }

  protected def checkNoCompletion(fileText: String,
                                  item: String,
                                  completionType: CompletionType = BASIC,
                                  time: Int = DEFAULT_TIME): Unit =
    checkNoCompletion(fileText, completionType, time) {
      hasLookupString(_, item)
    }

  protected final def checkNoCompletion(fileText: String,
                                        completionType: CompletionType,
                                        time: Int)
                                       (predicate: LookupElement => Boolean): Unit = {
    configureFromFileText(fileText)

    val lookups = getFixture.complete(completionType, time)
    assertFalse(lookups != null && lookups.exists(predicate))
  }

  protected final def configureTest(fileText: String,
                                    completionType: CompletionType = BASIC,
                                    time: Int = DEFAULT_TIME): Unit = {
    configureFromFileText(fileText)

    new CodeCompletionHandlerBase(completionType, false, false, true)
      .invokeCompletion(getProject, getEditor, time, false)
  }

  protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    getFixture.checkResult(normalize(expectedFileText), ignoreTrailingSpaces)
}

object ScalaCodeInsightTestBase {

  val DEFAULT_TIME: Int = 1

  object LookupString {

    def unapply(lookup: LookupElement) = Some(lookup.getLookupString)
  }

  def hasLookupString(lookup: LookupElement, lookupString: String): Boolean =
    lookup.getLookupString == lookupString

  def hasItemText(lookup: LookupElement,
                  lookupString: String)
                 (itemText: String = lookupString,
                  itemTextItalic: Boolean = false,
                  itemTextBold: Boolean = false,
                  tailText: String = null,
                  grayed: Boolean = false): Boolean = lookup match {
    case LookupString(`lookupString`) =>
      val presentation = new LookupElementPresentation
      lookup.renderElement(presentation)
      presentation.getItemText == itemText &&
        presentation.isItemTextItalic == itemTextItalic &&
        presentation.isItemTextBold == itemTextBold &&
        presentation.getTailText == tailText &&
        presentation.isTailGrayed == grayed
    case _ => false
  }
}
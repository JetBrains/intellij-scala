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
import org.junit.Assert.{assertEquals, fail}

import scala.collection.JavaConverters

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

  protected final def activeLookup: Option[LookupImpl] =
    LookupManager.getActiveLookup(getEditor) match {
      case impl: LookupImpl => Some(impl)
      case _ => None
    }

  protected final def lookups(predicate: LookupElement => Boolean): Seq[LookupElement] =
    activeLookup match {
      case Some(lookup) => lookupItems(lookup).filter(predicate)
      case _ => Seq.empty
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

    val maybePair = for {
      lookup <- activeLookup
      item <- lookupItems(lookup).find(predicate)
    } yield (lookup, item)

    maybePair match {
      case Some((lookup, item)) =>
        lookup.finishLookup(char, item)
        checkResultByText(resultText)
      case _ => fail("Lookups not found")
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
    assertEquals(count, lookups(predicate).size)
  }

  protected def checkNoCompletion(fileText: String,
                                  item: String,
                                  completionType: CompletionType = BASIC,
                                  time: Int = DEFAULT_TIME): Unit =
    doMultipleCompletionTest(fileText, 0, item, completionType, time)

  protected final def checkNoCompletion(fileText: String,
                                        completionType: CompletionType,
                                        time: Int)
                                       (predicate: LookupElement => Boolean): Unit =
    doMultipleCompletionTest(fileText, completionType, time, 0)(predicate)

  protected final def configureTest(fileText: String,
                                    completionType: CompletionType = BASIC,
                                    time: Int = DEFAULT_TIME): Unit = {
    configureFromFileText(fileText)

    new CodeCompletionHandlerBase(completionType, false, false, true)
      .invokeCompletion(getProject, getEditor, time, false, false)
  }

  protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    getFixture.checkResult(normalize(expectedFileText), ignoreTrailingSpaces)
}

object ScalaCodeInsightTestBase {

  val DEFAULT_TIME: Int = 1

  def hasLookupString(lookup: LookupElement, lookupString: String): Boolean =
    lookup.getLookupString == lookupString

  def hasItemText(lookup: LookupElement,
                  lookupString: String,
                  itemText: String,
                  itemTextItalic: Boolean = false,
                  tailText: String = null): Boolean =
    hasLookupString(lookup, lookupString) && {
      val presentation = new LookupElementPresentation
      lookup.renderElement(presentation)
      presentation.getItemText == itemText &&
        presentation.isItemTextItalic == itemTextItalic &&
        presentation.getTailText == tailText
    }

  private def lookupItems(lookup: LookupImpl) = {
    import JavaConverters._
    lookup.getItems.asScala
  }
}
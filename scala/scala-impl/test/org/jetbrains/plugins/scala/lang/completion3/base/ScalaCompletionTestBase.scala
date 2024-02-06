package org.jetbrains.plugins.scala.lang.completion3.base

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{Lookup, LookupElement, LookupElementPresentation}
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.fixtures.TestLookupElementPresentation
import org.jetbrains.plugins.scala.CompletionTests
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.util.runners.{MultipleScalaVersionsRunner, RunWithScalaVersions, TestScalaVersion}
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith

@RunWith(classOf[MultipleScalaVersionsRunner])
@RunWithScalaVersions(Array(
  TestScalaVersion.Scala_2_13,
  TestScalaVersion.Scala_3_Latest
))
@Category(Array(classOf[CompletionTests]))
abstract class ScalaCompletionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  import CompletionType.BASIC
  import Lookup.REPLACE_SELECT_CHAR
  import ScalaCompletionTestBase._

  protected lazy val scalaCompletionTestFixture: ScalaCompletionTestFixture = new ScalaCompletionTestFixture(
    scalaFixture,
    DefaultInvocationCount
  )

  protected override def setUp(): Unit = {
    super.setUp()

    scalaCompletionTestFixture //init lazy val fixture

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
    itemsExtractor: LookupImpl => Iterable[LookupElement] = ScalaCompletionTestFixture.allItems,
  ): (LookupImpl, Iterable[LookupElement]) =
    scalaCompletionTestFixture.activeLookupWithItems(fileText, completionType, invocationCount, itemsExtractor)

  protected final def doCompletionTest(
    fileText: String,
    resultText: String,
    item: String,
    char: Char = REPLACE_SELECT_CHAR,
    invocationCount: Int = DefaultInvocationCount,
    completionType: CompletionType = BASIC
  ): Unit =
    scalaCompletionTestFixture.doCompletionTest(fileText, resultText, item, char, invocationCount, completionType)

  protected final def doRawCompletionTest(
    fileText: String,
    resultText: String,
    char: Char = REPLACE_SELECT_CHAR,
    invocationCount: Int = DefaultInvocationCount,
    completionType: CompletionType = BASIC)
    (predicate: LookupElement => Boolean = Function.const(true)): Unit = {
    scalaCompletionTestFixture.doRawCompletionTest(fileText, resultText, char, invocationCount, completionType)(predicate)
  }

  protected final def checkNoBasicCompletion(
    fileText: String,
    item: String,
    invocationCount: Int = DefaultInvocationCount): Unit =
    scalaCompletionTestFixture.checkNoBasicCompletion(fileText, item, invocationCount)

  protected final def checkNoCompletion(
    fileText: String,
    `type`: CompletionType = BASIC,
    invocationCount: Int = DefaultInvocationCount)
    (predicate: LookupElement => Boolean = Function.const(true)): Unit =
    scalaCompletionTestFixture.checkNoCompletion(fileText, `type`, invocationCount)(predicate)

  protected final def checkNonEmptyCompletionWithKeyAbortion(
    fileText: String,
    resultText: String,
    char: Char,
    invocationCount: Int = DefaultInvocationCount,
    completionType: CompletionType = BASIC
  ): Unit =
    scalaCompletionTestFixture.checkNonEmptyCompletionWithKeyAbortion(fileText, resultText, char, invocationCount, completionType)

  protected final def checkEmptyCompletionAbortion(
    fileText: String,
    resultText: String,
    char: Char = REPLACE_SELECT_CHAR,
    invocationCount: Int = DefaultInvocationCount,
    completionType: CompletionType = BASIC
  ): Unit =
    scalaCompletionTestFixture.checkEmptyCompletionAbortion(fileText, resultText, char, invocationCount, completionType)

  protected final def completeBasic(invocationCount: Int): Array[LookupElement] =
    scalaCompletionTestFixture.completeBasic(invocationCount)

  protected final def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    scalaCompletionTestFixture.checkResultByText(expectedFileText, ignoreTrailingSpaces)
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
        ScalaCompletionTestFixture.isTailGrayed(presentation) == grayed
    case _ =>
      false
  }
}
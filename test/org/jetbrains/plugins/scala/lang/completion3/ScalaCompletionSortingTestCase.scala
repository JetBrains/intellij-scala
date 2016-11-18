package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.{CompletionLookupArranger, CompletionType, LightFixtureCompletionTestCase}
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.ui.UISettings
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import org.jetbrains.plugins.scala.util.TestUtils

/**
  * Created by kate
  * on 2/10/16
  */
abstract class ScalaCompletionSortingTestCase(completionType: CompletionType,
                                     relativePath: String) extends LightFixtureCompletionTestCase {

  def this(relativePath: String) {
    this(CompletionType.BASIC, relativePath)
  }

  @throws[Exception]
  override protected def setUp():Unit =  {
    super.setUp()
    StatisticsManager.getInstance.asInstanceOf[StatisticsManagerImpl].enableStatistics(getTestRootDisposable)
  }

  def baseRootPath: String = {
    TestUtils.getTestDataPath + relativePath
  }

  def invokeCompletion(path: String): LookupImpl = {
    configureNoCompletion(baseRootPath + path)
    myFixture.complete(completionType)
    getLookup
  }

  def invokeCompletionByText(name: String, text: String): LookupImpl = {
    configureNoCompletionByText(name, text)
    myFixture.complete(completionType)
    getLookup
  }

  def checkPreferredItems(selected: Int, expected: String*) {
    invokeCompletion(getTestName(false) + ".scala")
    assertPreferredItems(selected, expected: _*)
  }

  def assertPreferredItems(selected: Int, expected: String*) {
    myFixture.assertPreferredCompletionItems(selected, expected: _*)
  }

  def configureNoCompletion(path: String) {
    myFixture.configureFromExistingVirtualFile(
      myFixture.copyFileToProject(path, com.intellij.openapi.util.text.StringUtil.getShortName(path, '/')))
  }

  def configureNoCompletionByText(name: String, text: String) {
    myFixture.configureByText(name, text)
  }

  def incUseCount(lookup: LookupImpl, index: Int): Unit = {
    def refreshSorting(lookup: LookupImpl): Unit = {
      lookup.setSelectionTouched(false)
      lookup.resort(true)
    }

    def imitateItemSelection(lookup: LookupImpl, index: Int): Unit = {
      val item: LookupElement = lookup.getItems.get(index)
      lookup.setCurrentItem(item)
      CompletionLookupArranger.collectStatisticChanges(item, lookup)
      CompletionLookupArranger.applyLastCompletionStatisticsUpdate()
    }

    imitateItemSelection(lookup, index)
    refreshSorting(lookup)
  }

  @throws[Exception]
  override def tearDown() {
    LookupManager.getInstance(getProject).hideActiveLookup()
    UISettings.getInstance.SORT_LOOKUP_ELEMENTS_LEXICOGRAPHICALLY = false
    CodeInsightSettings.getInstance.COMPLETION_CASE_SENSITIVE = CodeInsightSettings.FIRST_LETTER
    super.tearDown()
  }
}
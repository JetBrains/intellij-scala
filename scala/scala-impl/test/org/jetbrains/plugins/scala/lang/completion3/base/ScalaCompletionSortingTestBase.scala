package org.jetbrains.plugins.scala.lang.completion3.base

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.StatisticsUpdate
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.util.text.StringUtil.getShortName
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import org.jetbrains.plugins.scala.util.TestUtils

abstract class ScalaCompletionSortingTestBase extends ScalaCompletionTestBase {

  override protected def setUp(): Unit = {
    super.setUp()

    StatisticsManager.getInstance match {
      case manager: StatisticsManagerImpl => manager.enableStatistics(getTestRootDisposable)
    }
  }

  override def tearDown(): Unit = try {
    LookupManager.getInstance(getProject).hideActiveLookup()
    UISettings.getInstance.setSortLookupElementsLexicographically(false)
    CodeInsightSettings.getInstance.setCompletionCaseSensitive(CodeInsightSettings.FIRST_LETTER)
  } finally {
    super.tearDown()
  }

  override def getTestDataPath: String = TestUtils.getTestDataPath + "/completion3/"

  private def invokeCompletion(): LookupImpl = {
    val path = getTestName(false) + ".scala"

    val projectFile = myFixture.copyFileToProject(path, getShortName(path, '/'))
    myFixture.configureFromExistingVirtualFile(projectFile)

    myFixture.completeBasic()
    getLookup
  }

  def getLookup =
    LookupManager.getActiveLookup(getEditor).asInstanceOf[LookupImpl]

  def checkFirst(expected: String*): Unit = {
    invokeCompletion()
    assertPreferredItems(expected: _*)
  }

  def assertPreferredItems(expected: String*): Unit = {
    myFixture.assertPreferredCompletionItems(0, expected: _*)
  }

  def incUseCount(): Unit = {
    val lookup = getLookup
    val item = lookup.getItems.get(1)

    StatisticsUpdate.collectStatisticChanges(item)
    StatisticsUpdate.applyLastCompletionStatisticsUpdate()

    lookup.setCurrentItem(item)
    lookup.setSelectionTouched(false)
    lookup.resort(true)
  }
}
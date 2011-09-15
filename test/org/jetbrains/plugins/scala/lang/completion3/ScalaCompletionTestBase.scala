package org.jetbrains.plugins.scala.lang.completion3

import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.codeInsight.completion.{CompletionType, CodeCompletionHandlerBase}
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.{LightPlatformTestCase, LightPlatformCodeInsightTestCase}

/**
 * @author Alexander Podkhalyuzin
 */

abstract class ScalaCompletionTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected override def setUp() {
    super.setUp()
    (StatisticsManager.getInstance.asInstanceOf[StatisticsManagerImpl]).clearStatistics()
  }

  protected def complete(time: Int = 1, completionType: CompletionType = CompletionType.BASIC) = {
    new CodeCompletionHandlerBase(completionType).
      invokeCompletion(getProjectAdapter, getEditorAdapter, time, false)
    val lookup: LookupImpl = LookupManager.getActiveLookup(getEditorAdapter).asInstanceOf[LookupImpl]
    (if (lookup == null) null else lookup.getItems.toArray(LookupElement.EMPTY_ARRAY),
    if (lookup == null) null else lookup.itemPattern(lookup.getItems.get(0)))
  }

  protected def completeLookupItem(item: LookupElement = null, completionChar: Char = '\t') {
     val lookup: LookupImpl = LookupManager.getActiveLookup(getEditorAdapter).asInstanceOf[LookupImpl]
    if (item == null) lookup.finishLookup(completionChar)
    else lookup.finishLookup(completionChar, item)
  }
}
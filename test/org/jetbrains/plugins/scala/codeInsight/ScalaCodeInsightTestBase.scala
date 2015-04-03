package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter

/**
 * @author Alexander Podkhalyuzin
 */

abstract class ScalaCodeInsightTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {
  protected override def setUp() {
    super.setUp()
    StatisticsManager.getInstance.asInstanceOf[StatisticsManagerImpl].enableStatistics(getTestRootDisposable)
  }

  protected def getActiveLookup: LookupImpl = {
    LookupManager.getActiveLookup(getEditorAdapter).asInstanceOf[LookupImpl]
  }

  protected def complete(time: Int = 1, completionType: CompletionType = CompletionType.BASIC) = {
    new CodeCompletionHandlerBase(completionType, false, false, true).
      invokeCompletion(getProjectAdapter, getEditorAdapter, time, false, false)
    val lookup: LookupImpl = getActiveLookup
    (if (lookup == null) null else lookup.getItems.toArray(LookupElement.EMPTY_ARRAY),
    if (lookup == null) null else lookup.itemPattern(lookup.getItems.get(0)))
  }

  protected def completeLookupItem(item: LookupElement = null, completionChar: Char = '\t') {
     val lookup: LookupImpl = getActiveLookup
    if (item == null) lookup.finishLookup(completionChar)
    else lookup.finishLookup(completionChar, item)
  }

  protected def invokeSmartEnter() {
    executeActionAdapter(IdeActions.ACTION_EDITOR_COMPLETE_STATEMENT)
  }
}
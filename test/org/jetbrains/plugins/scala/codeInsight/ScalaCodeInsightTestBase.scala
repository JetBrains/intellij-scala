package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupManager}
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.base.ScalaLightPlatformCodeInsightTestCaseAdapter
import org.junit.Assert.assertTrue

import scala.collection.JavaConverters

/**
  * @author Alexander Podkhalyuzin
  */
abstract class ScalaCodeInsightTestBase extends ScalaLightPlatformCodeInsightTestCaseAdapter {

  protected override def setUp(): Unit = {
    super.setUp()
    StatisticsManager.getInstance match {
      case impl: StatisticsManagerImpl => impl.enableStatistics(getTestRootDisposable)
    }
  }

  protected def getActiveLookup: Option[LookupImpl] =
    Option(getEditorAdapter)
      .map(LookupManager.getActiveLookup)
      .collect {
        case impl: LookupImpl => impl
      }

  protected def complete(time: Int = 1, completionType: CompletionType = CompletionType.BASIC): Seq[LookupElement] = {
    new CodeCompletionHandlerBase(completionType, false, false, true).
      invokeCompletion(getProjectAdapter, getEditorAdapter, time, false, false)

    import JavaConverters.asScalaBufferConverter
    getActiveLookup.toSeq
      .flatMap(_.getItems.asScala)
  }

  protected def finishLookup(item: LookupElement = null, completionChar: Char = '\t'): Unit =
    getActiveLookup.foreach {
      case impl if item == null => impl.finishLookup(completionChar)
      case impl => impl.finishLookup(completionChar, item)
    }

  protected def doCompletionTest(fileText: String, resultText: String, item: String, char: Char = '\t', time: Int = 1, completionType: CompletionType = CompletionType.BASIC): Unit = {
    configureFromFileTextAdapter("dummy.scala", normalize(fileText))

    val lookups = complete(time, completionType)
      .find(_.getLookupString == item)

    assertTrue(s"Completion list doesn't contain lookup `$item`", lookups.nonEmpty)

    lookups.foreach(finishLookup(_, char))

    checkResultByText(normalize(resultText))
  }
}
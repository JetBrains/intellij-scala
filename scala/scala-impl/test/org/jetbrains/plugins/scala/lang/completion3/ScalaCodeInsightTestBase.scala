package org.jetbrains.plugins.scala
package lang
package completion3

import com.intellij.codeInsight.completion.{CodeCompletionHandlerBase, CompletionType}
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.{LookupElement, LookupElementPresentation, LookupManager}
import com.intellij.openapi.vfs.VfsUtil.saveText
import com.intellij.psi.PsiFile
import com.intellij.psi.statistics.StatisticsManager
import com.intellij.psi.statistics.impl.StatisticsManagerImpl
import com.intellij.testFramework.LightPlatformTestCase.getSourceRoot
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestAdapter.normalize
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}

import scala.collection.JavaConverters

/**
  * @author Alexander Podkhalyuzin
  */
abstract class ScalaCodeInsightTestBase extends ScalaLightCodeInsightFixtureTestAdapter {

  import ScalaCodeInsightTestBase._

  protected override def setUp(): Unit = {
    super.setUp()
    StatisticsManager.getInstance match {
      case impl: StatisticsManagerImpl => impl.enableStatistics(getTestRootDisposable)
    }
  }

  override def getTestDataPath: String =
    s"${super.getTestDataPath}completion3/"

  protected def getActiveLookup: Option[LookupImpl] =
    Option(LookupManager.getActiveLookup(getEditor)).collect {
      case impl: LookupImpl => impl
    }

  protected def doCompletionTest(fileText: String,
                                 resultText: String,
                                 item: String,
                                 char: Char = DEFAULT_CHAR,
                                 time: Int = DEFAULT_TIME,
                                 completionType: CompletionType = DEFAULT_COMPLETION_TYPE): Unit =
    doCompletionTest(fileText, resultText, char, time, completionType) {
      hasLookupString(_, item)
    }

  protected def doCompletionTest(fileText: String,
                                 resultText: String,
                                 char: Char,
                                 time: Int,
                                 completionType: CompletionType)
                                (predicate: LookupElement => Boolean): Unit = {
    val lookups = configureTest(fileText, time, completionType)(predicate)
    assertFalse(lookups.isEmpty)

    val lookupElement = lookups.head
    getActiveLookup.foreach(_.finishLookup(char, lookupElement))
    checkResultByText(resultText)
  }

  protected def doMultipleCompletionTest(fileText: String,
                                         count: Int,
                                         item: String,
                                         char: Char = DEFAULT_CHAR,
                                         time: Int = DEFAULT_TIME,
                                         completionType: CompletionType = DEFAULT_COMPLETION_TYPE): Unit =
    doMultipleCompletionTest(fileText, count, char, time, completionType) {
      hasLookupString(_, item)
    }

  protected def doMultipleCompletionTest(fileText: String,
                                         count: Int,
                                         char: Char,
                                         time: Int,
                                         completionType: CompletionType)
                                        (predicate: LookupElement => Boolean): Unit = {
    val lookups = configureTest(fileText, time, completionType)(predicate)
    assertEquals(count, lookups.size)
  }

  protected def checkNoCompletion(fileText: String,
                                  item: String,
                                  time: Int = DEFAULT_TIME,
                                  completionType: CompletionType = DEFAULT_COMPLETION_TYPE): Unit =
    checkNoCompletion(fileText, time, completionType) {
      hasLookupString(_, item)
    }

  protected def checkNoCompletion(fileText: String,
                                  time: Int,
                                  completionType: CompletionType)
                                 (predicate: LookupElement => Boolean): Unit = {
    val lookups = configureTest(fileText, time, completionType)(predicate)
    assertTrue(lookups.isEmpty)
  }

  protected def configureTest(fileText: String,
                              time: Int = DEFAULT_TIME,
                              completionType: CompletionType = DEFAULT_COMPLETION_TYPE)
                             (predicate: LookupElement => Boolean = _ => true): Seq[LookupElement] = {
    configureFromFileText(fileText)

    new CodeCompletionHandlerBase(completionType, false, false, true).
      invokeCompletion(getProject, getEditor, time, false, false)

    import JavaConverters.asScalaBufferConverter
    getActiveLookup.toSeq
      .flatMap(_.getItems.asScala)
      .filter(predicate)
  }

  protected def configureJavaFile(fileText: String, className: String, packageName: String): Unit = inWriteAction {
    val file = getSourceRoot.createChildDirectory(null, packageName)
      .createChildData(null, s"$className.java")
    saveText(file, normalize(fileText))
  }

  protected def configureFromFileText(fileText: String): PsiFile =
    getFixture.configureByText(ScalaFileType.INSTANCE, normalize(fileText))

  protected def checkResultByText(expectedFileText: String, ignoreTrailingSpaces: Boolean = true): Unit =
    getFixture.checkResult(normalize(expectedFileText), ignoreTrailingSpaces)
}

object ScalaCodeInsightTestBase {

  val DEFAULT_CHAR: Char = '\t'
  val DEFAULT_TIME: Int = 1
  val DEFAULT_COMPLETION_TYPE: CompletionType = CompletionType.BASIC

  def hasLookupString(lookup: LookupElement, item: String): Boolean =
    lookup.getLookupString == item

  def renderLookupElement(lookup: LookupElement): LookupElementPresentation = {
    val presentation = new LookupElementPresentation
    lookup.renderElement(presentation)
    presentation
  }
}
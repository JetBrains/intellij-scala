package org.jetbrains.plugins.scala.base

import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable
import org.jetbrains.plugins.scala.{CompletionTests, ScalaFileType}
import org.junit.experimental.categories.Category

/** @see [[com.intellij.codeInsight.completion.JavaCompletionAutoPopupTestCase]] */
@Category(Array(classOf[CompletionTests]))
abstract class ScalaCompletionAutoPopupTestCase extends ScalaLightCodeInsightFixtureTestCase {
  private[this] var myTester: CompletionAutoPopupTester = _

  override protected def setUp(): Unit = {
    super.setUp()
    myTester = new CompletionAutoPopupTester(myFixture)
  }

  override protected def runInDispatchThread(): Boolean = false

  override protected def runTestRunnable(testRunnable: ThrowableRunnable[Throwable]): Unit =
    myTester.runWithAutoPopupEnabled(testRunnable)

  protected def getLookup: LookupImpl = myTester.getLookup

  protected def doType(textToType: String): Unit =
    myTester.typeWithPauses(textToType)

  protected def fileType: FileType = ScalaFileType.INSTANCE

  private def appendExtension(fileName: String): String =
    s"$fileName.${fileType.getDefaultExtension}"

  private def defaultFileName: String = appendExtension("aaa")

  protected def configureByText(text: String): PsiFile =
    myFixture.configureByText(defaultFileName, text)

  protected def configureByFile(fileName: String): PsiFile =
    myFixture.configureByFile(appendExtension(fileName))
}

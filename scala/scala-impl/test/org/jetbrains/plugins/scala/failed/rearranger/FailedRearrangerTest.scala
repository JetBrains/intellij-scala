package org.jetbrains.plugins.scala.failed.rearranger

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.arrangement.engine.ArrangementEngine
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.base.SdkFileSetTestBase
import org.junit.Assert.assertNotNull

import java.nio.file.Path

class FailedRearrangerTest extends SdkFileSetTestBase {
  override protected def relativeTestDataPath: Path = Path.of("rearranger", "failedData")

  override protected def shouldPass: Boolean = false

  override protected def transform(testName: String, fileText: String): String = {
    val file = createLightFile(fileText)
    val runnable: Runnable = { () =>
      try rearrange(file)
      catch { case e: IncorrectOperationException => e.printStackTrace() }
    }
    WriteCommandAction.runWriteCommandAction(project, runnable)
    file.getText
  }

  private def rearrange(file: PsiFile): Unit = {
    ArrangementEngine.getInstance().arrange(file, java.util.List.of(file.getTextRange))
    val documentManager = PsiDocumentManager.getInstance(project)
    val document = documentManager.getDocument(file)
    assertNotNull("Wrong PsiFile type provided: the file has no document.", document)
    documentManager.commitDocument(document)
  }
}

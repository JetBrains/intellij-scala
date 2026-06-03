package org.jetbrains.plugins.scala.lang.formatter.intellij

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.base.NoSdkFileSetTestBase
import org.jetbrains.plugins.scala.extensions.inWriteAction

abstract class FormatterTestBase extends NoSdkFileSetTestBase {

  override def setUp(): Unit = {
    super.setUp()
    scalaCodeStyleSettings.USE_SCALADOC2_FORMATTING = true
  }

  override protected def transform(testName: String, fileText: String): String = {
    val psiFile = createLightFile(fileText)
    val runnable: Runnable = () => inWriteAction {
      try CodeStyleManager.getInstance(project).reformatText(psiFile, java.util.List.of(psiFile.getTextRange))
      catch { case e: IncorrectOperationException => e.printStackTrace() }
    }
    WriteCommandAction.runWriteCommandAction(project, runnable)
    psiFile.getText
  }
}

package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.compiler.{CompileContext, CompileTask, CompilerManager}
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings

class ReformatOnCompileTask(val project: Project) extends AbstractProjectComponent(project) with CompileTask {
  override def execute(context: CompileContext): Boolean = {
    val commonSettings = CodeStyle.getSettings(project)
    val settings = commonSettings.getCustomSettings(classOf[ScalaCodeStyleSettings])
    if (!settings.REFORMAT_ON_COMPILE) return true
    val srcVFiles = context.getCompileScope.getFiles(ScalaFileType.INSTANCE, true)
    val manager = PsiManager.getInstance(project)
    val codeStyleManager = CodeStyleManager.getInstance(project)
    for (psiFile <- srcVFiles.map { vFile => inReadAction(manager.findFile(vFile)) }.filter(_ != null)) {
      ApplicationManager.getApplication.invokeAndWait {CommandProcessor.getInstance().runUndoTransparentAction(() => codeStyleManager.reformat(psiFile))}
    }
    true
  }

  override def projectOpened(): Unit = {
    CompilerManager.getInstance(project).addBeforeTask(this)
  }
}

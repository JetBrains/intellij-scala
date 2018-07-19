package org.jetbrains.plugins.scala
package lang
package formatting

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationManager.{getApplication => Application}
import com.intellij.openapi.command.CommandProcessor.{getInstance => CommandProcessor}
import com.intellij.openapi.compiler._
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

class ReformatOnCompileTask(project: Project) extends AbstractProjectComponent(project) with CompileTask {

  override def execute(context: CompileContext): Boolean = {
    CodeStyle.getSettings(myProject)
      .getCustomSettings(classOf[settings.ScalaCodeStyleSettings]) match {
      case settings if settings.REFORMAT_ON_COMPILE =>
        reformatScopeFiles(context.getCompileScope)
      case _ =>
    }

    true
  }

  override def projectOpened(): Unit = {
    CompilerManager.getInstance(myProject).addBeforeTask(this)
  }

  private def reformatScopeFiles(compileScope: CompileScope): Unit = for {
    virtualFile <- compileScope.getFiles(ScalaFileType.INSTANCE, true)

    psiManager = PsiManager.getInstance(myProject)
    psiFile <- inReadAction(psiManager.findFile(virtualFile).asOptionOf[ScalaFile].filterNot(_.isWorksheetFile))
  } {
    Application.invokeAndWait {
      CommandProcessor.runUndoTransparentAction {
        CodeStyleManager.getInstance(myProject).reformat(psiFile)
      }
    }
  }
}

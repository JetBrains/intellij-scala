package org.jetbrains.plugins.scala.lang.formatting

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.compiler._
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.ScalafmtDynamicConfigService
import org.jetbrains.plugins.scala.lang.formatting.scalafmt.processors.ScalaFmtPreFormatProcessor
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.util.compile.ScalaCompileTask

final class ReformatOnCompileTask(project: Project) extends ScalaCompileTask {

  override protected def run(context: CompileContext): Boolean = {
    val scalaSettings = codeStyleSettings
    if (scalaSettings.REFORMAT_ON_COMPILE) {
      ScalaFmtPreFormatProcessor.inFailSilentMode {
        reformatScopeFiles(context.getCompileScope, scalaSettings)
      }
    }
    true
  }

  @Nls
  override protected def presentableName: String = ScalaBundle.message("reformat.on.compile.task.presentable.name")

  override protected def shouldLogToBuildOutput: Boolean = codeStyleSettings.REFORMAT_ON_COMPILE

  private def codeStyleSettings: ScalaCodeStyleSettings =
    CodeStyle.getSettings(project).getCustomSettings(classOf[ScalaCodeStyleSettings])

  private def reformatScopeFiles(compileScope: CompileScope, scalaSettings: ScalaCodeStyleSettings): Unit = for {
    virtualFile <- compileScope.getFiles(ScalaFileType.INSTANCE, true)
    psiFile = inReadAction(PsiManager.getInstance(project).findFile(virtualFile))
    if shouldFormatFile(psiFile, scalaSettings)
    psiFile <- psiFile.asOptionOf[ScalaFile].filterNot(_.isWorksheetFile)
  } {
    invokeAndWait { () =>
      CommandProcessor.getInstance().runUndoTransparentAction { () =>
        CodeStyleManager.getInstance(project).reformat(psiFile)
      }
    }
  }

  private def shouldFormatFile(file: PsiFile, scalaSettings: ScalaCodeStyleSettings): Boolean = {
    if (scalaSettings.USE_SCALAFMT_FORMATTER()) {
      ScalafmtDynamicConfigService.isIncludedInProject(file)
    } else {
      true
    }
  }
}

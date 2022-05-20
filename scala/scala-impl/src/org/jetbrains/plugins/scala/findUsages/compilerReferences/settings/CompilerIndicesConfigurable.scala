package org.jetbrains.plugins.scala.findUsages.compilerReferences
package settings

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._

import javax.swing.JComponent

class CompilerIndicesConfigurable(project: Project) extends Configurable {
  import CompilerIndicesConfigurable._

  private[this] val panel                              = new CompilerIndicesSettingsForm(project)
  private[this] var shutdownCallback: Option[Runnable] = None

  override def getDisplayName: String        = ScalaBundle.message("bytecode.indices")
  override def createComponent(): JComponent = panel.mainPanel
  override def isModified: Boolean           = panel.isModified(CompilerIndicesSettings(project), CompilerIndicesSbtSettings())
  override def reset(): Unit                 = panel.from(CompilerIndicesSettings(project), CompilerIndicesSbtSettings())

  override def apply(): Unit                 = {
    val requiresRestart = panel.applyTo(CompilerIndicesSettings(project), CompilerIndicesSbtSettings())

    if (requiresRestart) {
      shutdownCallback = Option(() => invokeLater { shutdownOrRestartApp() })
    }
  }

  override def disposeUIResources(): Unit = shutdownCallback.foreach(_.run())
}

object CompilerIndicesConfigurable {
  private def shutdownOrRestartApp(): Unit =
    if (showRestartDialog() == Messages.YES)
      ApplicationManagerEx.getApplicationEx.restart(true)

  private def showRestartDialog(): Int = {
    val canRestart = ApplicationManagerEx.getApplicationEx.isRestartCapable
    val action =
      if (canRestart) IdeBundle.message("ide.restart.action")
      else IdeBundle.message("ide.shutdown.action")
    val message =
      if (canRestart) ScalaBundle.message("bytecode.indices.restart.message")
      else ScalaBundle.message("bytecode.indices.shutdown.message")
    val title   = ScalaBundle.message("bytecode.indices.restart.title")

    Messages.showYesNoDialog(message, title, action, IdeBundle.message("ide.postpone.action"), Messages.getQuestionIcon)
  }
}

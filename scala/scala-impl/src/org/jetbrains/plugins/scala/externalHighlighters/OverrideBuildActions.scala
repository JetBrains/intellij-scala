package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.actionSystem.{AnAction, AnActionEvent, IdeActions}
import com.intellij.openapi.compiler.CompilerManager
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.actions.ScalaActionUtil
import org.jetbrains.plugins.scala.externalHighlighters.OverrideBuildActions.Decorated

class OverrideBuildActions
  extends ApplicationInitializedListener {

  override def componentsInitialized(): Unit =
    Seq(
      IdeActions.ACTION_COMPILE,
      IdeActions.ACTION_COMPILE_PROJECT,
      IdeActions.ACTION_MAKE_MODULE,
      "CompileDirty",
      "Debugger.ReloadFile"
    ).foreach { actionId =>
      ScalaActionUtil.decorateAction(actionId, new Decorated(_))
    }
}

object OverrideBuildActions {

  class Decorated(delegate: AnAction)
    extends AnAction {

    copyFrom(delegate)

    override def actionPerformed(e: AnActionEvent): Unit =
      Option(e.getProject).foreach { project =>
        JpsCompiler.get(project).setBuildActionAllowed(false)
        delegate.actionPerformed(e)
      }

    override def update(e: AnActionEvent): Unit = {
      delegate.update(e)
      val enabled = Option(e.getProject).forall(actionEnabled)
      e.getPresentation.setEnabled(enabled)
    }

    private def actionEnabled(project: Project): Boolean = {
      val anyCompilationActive = CompilerManager.getInstance(project).isCompilationActive
      val allowBuildAction = JpsCompiler.get(project).buildActionAllowed
      !anyCompilationActive || allowBuildAction
    }
  }
}

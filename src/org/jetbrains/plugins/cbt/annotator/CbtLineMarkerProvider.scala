package org.jetbrains.plugins.cbt.annotator

import java.awt.event.MouseEvent
import java.nio.file.Paths
import java.util

import com.intellij.codeHighlighting.Pass
import com.intellij.codeInsight.daemon.{GutterIconNavigationHandler, LineMarkerInfo, LineMarkerProvider}
import com.intellij.execution.ExecutionManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.{DefaultJavaProgramRunner, RunManagerImpl, RunnerAndConfigurationSettingsImpl}
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.settings.CbtProjectSettings
import org.jetbrains.plugins.cbt.runner.CbtProcessListener
import org.jetbrains.plugins.cbt.runner.internal.{CbtBuildConfigurationFactory, CbtBuildConfigurationType}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl

import scala.util.Try

class CbtLineMarkerProvider extends LineMarkerProvider {
  override def getLineMarkerInfo(element: PsiElement): LineMarkerInfo[_ <: PsiElement] = {
    val project = element.getProject
    if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER &&
      project.isCbtProject) {
      val range = element.getTextRange
      (for {
        parent <- Option(element.getParent)
        wrapper <- Option(parent.getParent.getParent.getParent)
        f <- Try(parent.asInstanceOf[ScFunction]).toOption
        c <- Try(wrapper.asInstanceOf[ScClass]).toOption
        if f.nameId == element && c.name == "Build" //TODO change to inherits from Build
        m <- createRunMarker(project, range, f)
      } yield m).orNull
    } else null
  }

  private def createRunMarker(project: Project, range: TextRange, scFun: ScFunction): Option[LineMarkerInfo[_ <: PsiElement]] = {
    val tooltipHandler = new com.intellij.util.Function[PsiElement, String] {
      override def fun(param: PsiElement): String = "Run"
    }
    val handler = new GutterIconNavigationHandler[PsiElement] {
      override def navigate(e: MouseEvent, elt: PsiElement): Unit = {
        val projectSettings = CbtProjectSettings.getInstance(project, project.getBasePath)
        val dir = {
          val modules = ModuleManager.getInstance(project).getModules.toSeq.sortBy(_.getName.length.unary_-)
          val fileDir = Paths.get(elt.getContainingFile.getContainingDirectory.getVirtualFile.getPath)
          modules.find(m => fileDir.startsWith(m.getModuleFile.getParent.getCanonicalPath))
            .map(_.getModuleFile.getParent.getCanonicalPath)
            .get
        }
        val task = elt.getParent.asInstanceOf[ScFunctionDefinitionImpl].getName
        val configuration = new CbtBuildConfigurationFactory(task, projectSettings.useDirect,
          dir, Seq.empty, CbtBuildConfigurationType.getInstance, CbtProcessListener.Dummy)
          .createTemplateConfiguration(project)
        val runnerSettings = new RunnerAndConfigurationSettingsImpl(RunManagerImpl.getInstanceImpl(project), configuration)
        val environment = new ExecutionEnvironment(DefaultRunExecutor.getRunExecutorInstance, DefaultJavaProgramRunner.getInstance, runnerSettings, project)
        ExecutionManager.getInstance(project).restartRunProfile(environment)
      }
    }
    //TODO add debugging
    Some(new LineMarkerInfo[PsiElement](scFun.nameId, range, AllIcons.General.Run, Pass.UPDATE_ALL, tooltipHandler, handler, GutterIconRenderer.Alignment.LEFT))
  }

  override def collectSlowLineMarkers(elements: util.List[PsiElement], result: util.Collection[LineMarkerInfo[_ <: PsiElement]]): Unit = ()
}

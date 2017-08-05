package org.jetbrains.plugins.cbt.annotator

import java.nio.file.Paths

import com.intellij.execution.lineMarker.RunLineMarkerContributor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.runner.action.{DebugTaskAction, RunTaskAction}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl

import scala.util.Try

class CbtLineMarkerProvider extends RunLineMarkerContributor {
  override def getInfo(element: PsiElement): RunLineMarkerContributor.Info = {
    val project = element.getProject

    def isBuildClass(scClass: ScClass) =
      scClass.supers
        .collect { case t: ScTrait => t.name; case c: ScClass => c.name }
        .toSet
        .intersect(CBT.cbtBuildClassNames.toSet)
        .nonEmpty

    if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER &&
      project.isCbtProject) {
      val range = element.getTextRange
      (for {
        parent <- Option(element.getParent)
        wrapper <- Option(parent.getParent.getParent.getParent)
        f <- Try(parent.asInstanceOf[ScFunction]).toOption
        c <- Try(wrapper.asInstanceOf[ScClass]).toOption
        if f.nameId == element && isBuildClass(c) //TODO change to inherits from Build
        m <- createRunMarker(project, range, f)
      } yield m).orNull
    } else null
  }

  private def createRunMarker(project: Project, range: TextRange, scFun: ScFunction): Option[RunLineMarkerContributor.Info] = {
    val tooltipHandler = new com.intellij.util.Function[PsiElement, String] {
      override def fun(param: PsiElement): String = "Run"
    }
    val dir = {
      val modules = ModuleManager.getInstance(project).getModules.toSeq.sortBy(_.baseDir.length.unary_-)
      val fileDir = Paths.get(scFun.getContainingFile.getContainingDirectory.getVirtualFile.getPath)
      modules
        .find(m => fileDir.startsWith(m.getModuleFile.getParent.getCanonicalPath))
        .map(_.getModuleFile.getParent.getParent.getCanonicalPath)
        .get
    }
    val module = {
      val buildModule = CBT.moduleByPath(scFun.getContainingFile.getVirtualFile.getPath, project)
      val moudleDir = buildModule.baseDir.toFile.toPath.getParent.toString
      CBT.moduleByPath(moudleDir, project)
    }
    val task = scFun.asInstanceOf[ScFunctionDefinitionImpl].getName
    val actions: Array[AnAction] =
      Array(new RunTaskAction(task, module, project), new DebugTaskAction(task, module, project))
    val info = new RunLineMarkerContributor.Info(AllIcons.General.Run, tooltipHandler, actions: _ *)
    Some(info)
  }
}

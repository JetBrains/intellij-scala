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

    if (element.getNode.getElementType == ScalaTokenTypes.tIDENTIFIER
      && project.isCbtProject) {
      val range = element.getTextRange
      (for {
        parent <- Option(element.getParent)
        wrapper <- Option(parent.getParent.getParent.getParent)
        f <- Try(parent.asInstanceOf[ScFunction]).toOption
        c <- Try(wrapper.asInstanceOf[ScClass]).toOption
        if f.nameId == element && isBuildClass(c)
        m <- createRunMarker(project, range, f)
      } yield m).orNull
    } else null
  }

  private def createRunMarker(project: Project,
                              range: TextRange,
                              scFun: ScFunction): Option[RunLineMarkerContributor.Info] = {
    val task = scFun.asInstanceOf[ScFunctionDefinitionImpl].getName
    val tooltipHandler = (_: PsiElement) => s"Run or Debug task '$task'"
    val module =
      CBT.moduleByPath(scFun.getContainingFile.getVirtualFile.getPath, project)
        .flatMap { buildModule =>
          val moudleDir = buildModule.baseDir.toFile.toPath.getParent.toString
          CBT.moduleByPath(moudleDir, project)
        }
    module.map { m =>
      val actions: Array[AnAction] =
        Array(new RunTaskAction(task, m, project), new DebugTaskAction(task, m, project))
      new RunLineMarkerContributor.Info(AllIcons.General.Run, tooltipHandler, actions: _ *)
    }
  }
}

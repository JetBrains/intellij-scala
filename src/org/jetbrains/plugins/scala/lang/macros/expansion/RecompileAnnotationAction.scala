package org.jetbrains.plugins.scala.lang.macros.expansion

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.compiler.{CompileContext, CompileStatusNotification, CompilerManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation
import org.jetbrains.plugins.scala.project._

class RecompileAnnotationAction(elt: ScAnnotation) extends ScalaMetaIntentionAction {

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = true
  override def getText: String = ScalaBundle.message("scala.meta.recompile")

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    CompilerManager.getInstance(elt.getProject).make(elt.constructor.reference.get.resolve().module.get,
      new CompileStatusNotification {
        override def finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext): Unit = {
          DaemonCodeAnalyzer.getInstance(elt.getProject).restart(elt.getContainingFile)
        }
      }
    )
  }
}

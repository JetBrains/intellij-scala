package org.jetbrains.plugins.scala
package codeInspection.relativeImports

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 14.09.12
 */
class RelativeImportInspection extends AbstractInspection("RelativeImport", "Relative Import") {
  import org.jetbrains.plugins.scala.codeInspection.relativeImports.RelativeImportInspection.qual

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScImportExpr if expr.qualifier != null =>
      val q = qual(expr.qualifier)
      val resolve = q.multiResolve(false)
      for (elem <- resolve) {
        def applyProblem(qualifiedName: String) {
          val fixes = new ArrayBuffer[LocalQuickFix]()
          if (!ScalaCodeStyleSettings.getInstance(q.getProject).isAddFullQualifiedImports) {
            fixes += new EnableFullQualifiedImports(q.getProject)
          }
          fixes += new MakeFullQualifiedImportFix(q, qualifiedName)
          holder.registerProblem(q, "Relative import detected", fixes: _*)
        }
        elem match {
          case ScalaResolveResult(p: PsiPackage, _) if p.getQualifiedName.contains(".") =>
            applyProblem(p.getQualifiedName)
          case ScalaResolveResult(c: ScObject, _) if c.isTopLevel && c.qualifiedName.contains(".") =>
            applyProblem(c.qualifiedName)
          case _ =>
        }
      }
  }
}

object RelativeImportInspection {
  @tailrec
  def qual(st: ScStableCodeReferenceElement): ScStableCodeReferenceElement = st.qualifier match {
    case Some(q) => qual(q)
    case _ => st
  }
}

private class EnableFullQualifiedImports(project: Project) extends LocalQuickFix {
  def getName: String = getFamilyName

  def getFamilyName: String = "Enable full qualified imports"

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    ScalaCodeStyleSettings.getInstance(project).setAddFullQualifiedImports(true)
  }
}

private class MakeFullQualifiedImportFix(q: ScStableCodeReferenceElement, fqn: String)
        extends AbstractFixOnPsiElement(ScalaBundle.message("make.import.fully.qualified"), q) {

  def doApplyFix(project: Project) {
    val ref = getElement
    if (ref == null || !ref.isValid) return
    val newRef = ScalaPsiElementFactory.createReferenceFromText(fqn, ref.getContext, ref)
    import org.jetbrains.plugins.scala.codeInspection.relativeImports.RelativeImportInspection.qual
    val newFqn = qual(newRef).resolve() match {
      case p: PsiPackage if p.getQualifiedName.contains(".") => "_root_." + fqn
      case p: PsiPackage => fqn
      case _ => "_root_." + fqn
    }
    ref.replace(ScalaPsiElementFactory.createReferenceFromText(newFqn, ref.getManager))
  }
}

package org.jetbrains.plugins.scala
package codeInspection.relativeImports

import codeInspection.AbstractInspection
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix, ProblemsHolder}
import com.intellij.psi.{PsiClass, PsiPackage, PsiElement}
import lang.psi.api.toplevel.imports.ScImportExpr
import lang.psi.api.base.ScStableCodeReferenceElement
import annotation.tailrec
import lang.resolve.ScalaResolveResult
import com.intellij.openapi.project.Project
import settings.ScalaProjectSettings
import collection.mutable.ArrayBuffer
import lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.extensions
import lang.psi.api.toplevel.typedef.ScTypeDefinition

/**
 * @author Alefas
 * @since 14.09.12
 */
class RelativeImportInspection extends AbstractInspection("RelativeImport", "Relative Import") {
  import RelativeImportInspection.qual

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScImportExpr =>
      val q = qual(expr.qualifier)
      val resolve = q.multiResolve(false)
      for (elem <- resolve) {
        def applyProblem(qualifiedName: String) {
          val fixes = new ArrayBuffer[LocalQuickFix]()
          if (ScalaProjectSettings.getInstance(q.getProject).isAddFullQualifiedImports) {
            fixes += new EnableFullQualifiedImports(q.getProject)
          }
          fixes += new MakeFullQualifiedImportFix(q, qualifiedName)
          holder.registerProblem(q, "Relative import detected", fixes: _*)
        }
        elem match {
          case ScalaResolveResult(p: PsiPackage, _) if p.getQualifiedName.contains(".") =>
            applyProblem(p.getQualifiedName)
          case ScalaResolveResult(c: ScTypeDefinition, _) if c.isTopLevel && c.qualifiedName.contains(".") =>
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
    ScalaProjectSettings.getInstance(project).setAddFullQualifiedImports(true)
  }
}

private class MakeFullQualifiedImportFix(q: ScStableCodeReferenceElement, fqn: String) extends LocalQuickFix {
  def getName: String = getFamilyName

  def getFamilyName: String = "Make import fully qualified"

  def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (q == null || !q.isValid) return
    val newRef = ScalaPsiElementFactory.createReferenceFromText(fqn, q.getContext, q)
    import RelativeImportInspection.qual
    val newFqn = qual(newRef).resolve() match {
      case p: PsiPackage if p.getQualifiedName.contains(".") => "_root_." + fqn
      case p: PsiPackage => fqn
      case _ => "_root_." + fqn
    }
    q.replace(ScalaPsiElementFactory.createReferenceFromText(newFqn, q.getManager))
  }
}

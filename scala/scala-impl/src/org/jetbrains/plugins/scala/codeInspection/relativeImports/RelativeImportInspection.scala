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
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 14.09.12
 */
class RelativeImportInspection extends AbstractInspection("RelativeImport", "Relative Import") {
  import org.jetbrains.plugins.scala.codeInspection.relativeImports.RelativeImportInspection.qual

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case expr: ScImportExpr if expr.qualifier != null =>
      val q = qual(expr.qualifier)
      val resolve = q.multiResolve(false)
      for (elem <- resolve) {
        def applyProblem(qualifiedName: String) {
          val fixes = new ArrayBuffer[LocalQuickFix]()
          if (!ScalaCodeStyleSettings.getInstance(q.getProject).isAddFullQualifiedImports) {
            fixes += new EnableFullQualifiedImports()
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

private class EnableFullQualifiedImports extends LocalQuickFix {
  override def getName: String = getFamilyName

  override def getFamilyName: String = "Enable full qualified imports"

  override def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    ScalaCodeStyleSettings.getInstance(project).setAddFullQualifiedImports(true)
  }
}

private class MakeFullQualifiedImportFix(q: ScStableCodeReferenceElement, fqn: String)
        extends AbstractFixOnPsiElement(ScalaBundle.message("make.import.fully.qualified"), q) {

  override protected def doApplyFix(ref: ScStableCodeReferenceElement)
                                   (implicit project: Project): Unit = {
    val newRef = createReferenceFromText(fqn, ref.getContext, ref)
    val newFqn = RelativeImportInspection.qual(newRef).resolve() match {
      case p: PsiPackage if p.getQualifiedName.contains(".") => "_root_." + fqn
      case _: PsiPackage => fqn
      case _ => "_root_." + fqn
    }
    ref.replace(createReferenceFromText(newFqn))
  }
}

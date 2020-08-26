package org.jetbrains.plugins.scala
package codeInspection.relativeImports

import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiPackage}
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceFromText

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 14.09.12
 */
class RelativeImportInspection extends AbstractInspection(ScalaInspectionBundle.message("display.name.relative.import")) {
  import org.jetbrains.plugins.scala.codeInspection.relativeImports.RelativeImportInspection.qual

  override def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case expr: ScImportExpr if expr.qualifier != null =>
      val q = qual(expr.qualifier)
      val resolve = q.multiResolveScala(false)
      for (result <- resolve) {
        def applyProblem(qualifiedName: String): Unit = {
          val fixes = new ArrayBuffer[LocalQuickFix]()
          if (!ScalaCodeStyleSettings.getInstance(q.getProject).isAddFullQualifiedImports) {
            fixes += new EnableFullQualifiedImports()
          }
          fixes += new MakeFullQualifiedImportFix(q, qualifiedName)
          holder.registerProblem(q, ScalaInspectionBundle.message("relative.import.detected"), fixes.toSeq: _*)
        }
        result.element match {
          case p: PsiPackage if p.getQualifiedName.contains(".") =>
            applyProblem(p.getQualifiedName)
          case c: ScObject if c.isTopLevel && c.qualifiedName.contains(".") =>
            applyProblem(c.qualifiedName)
          case _ =>
        }
      }
  }
}

object RelativeImportInspection {
  @tailrec
  def qual(st: ScStableCodeReference): ScStableCodeReference = st.qualifier match {
    case Some(q) => qual(q)
    case _ => st
  }
}

private class EnableFullQualifiedImports extends LocalQuickFix {
  override def getName: String = getFamilyName

  override def getFamilyName: String = ScalaInspectionBundle.message("family.name.enable.full.qualified.imports")

  override def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    ScalaCodeStyleSettings.getInstance(project).setAddFullQualifiedImports(true)
  }
}

private class MakeFullQualifiedImportFix(q: ScStableCodeReference, fqn: String)
        extends AbstractFixOnPsiElement(ScalaBundle.message("make.import.fully.qualified"), q) {

  override protected def doApplyFix(ref: ScStableCodeReference)
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

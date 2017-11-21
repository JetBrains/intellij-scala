package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}

/**
  * User: Dmitry.Naydanov
  * Date: 30.08.17.
  */
class AmmoniteUnresolvedLibraryInspection extends AbstractInspection("Unresolved Ivy import") {
  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case stableRef: ScStableCodeReferenceElement => stableRef.qualifier.foreach(processExpr(stableRef, _, holder))
    case selector: ScImportSelector =>
      new ParentsIterator(selector).find {
        case expr: ScImportExpr => selector.reference.foreach(processExpr(_, expr.qualifier, holder))
          true
        case _ => false
      }
  }
  
  private def processExpr(ref: ScReferenceElement, qualifier: ScStableCodeReferenceElement, holder: ProblemsHolder) {
    if (qualifier == null || qualifier.refName != "$ivy" || ref.resolve() != null) return
    AmmoniteScriptWrappersHolder.getInstance(ref.getProject).registerProblemIn(ref.getContainingFile.asInstanceOf[ScalaFile])
    holder.registerProblem(ref, "Cannot resolve import", ProblemHighlightType.WEAK_WARNING, null: TextRange,
      new CreateImportedLibraryQuickFix(ref))
  }
}

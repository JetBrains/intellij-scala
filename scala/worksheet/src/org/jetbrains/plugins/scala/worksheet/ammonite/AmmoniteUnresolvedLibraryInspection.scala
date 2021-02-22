package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle

import scala.annotation.nowarn

@nowarn("msg=" + AbstractInspection.DeprecationText)
class AmmoniteUnresolvedLibraryInspection extends AbstractInspection(WorksheetBundle.message("display.name.unresolved.ivy.import")) {
  override protected def actionFor(implicit holder: ProblemsHolder, isOnTheFly: Boolean): PartialFunction[PsiElement, Any] = {
    case stableRef: ScStableCodeReference => stableRef.qualifier.foreach(processExpr(stableRef, _, holder))
    case selector: ScImportSelector =>
      new ParentsIterator(selector).find {
        case expr: ScImportExpr => selector.reference.foreach(processExpr(_, expr.qualifier.get, holder))
          true
        case _ => false
      }
  }
  
  private def processExpr(ref: ScReference, qualifier: ScStableCodeReference, holder: ProblemsHolder): Unit = {
    if (qualifier == null || qualifier.refName != "$ivy" || ref.resolve() != null) return
    AmmoniteScriptWrappersHolder.getInstance(ref.getProject).registerProblemIn(ref.getContainingFile.asInstanceOf[ScalaFile])
    holder.registerProblem(ref, WorksheetBundle.message("ammonite.cannot.resolve.import"), ProblemHighlightType.WEAK_WARNING, null: TextRange,
      new CreateImportedLibraryQuickFix(ref))
  }
}

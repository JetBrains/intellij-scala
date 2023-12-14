package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInspection.{LocalInspectionTool, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.codeInspection.PsiElementVisitorSimple
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ParentsIterator
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.worksheet.WorksheetBundle

class AmmoniteUnresolvedLibraryInspection extends LocalInspectionTool {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case stableRef: ScStableCodeReference => processExpr(stableRef, stableRef.qualifier, holder)
    case selector: ScImportSelector =>
      new ParentsIterator(selector).find {
        case expr: ScImportExpr =>
          selector.reference.foreach(processExpr(_, expr.qualifier, holder))
          true
        case _ => false
      }
    case _ =>
  }

  private def processExpr(ref: ScReference, qualifier: Option[ScStableCodeReference], holder: ProblemsHolder): Unit = {
    if (qualifier.forall(_.refName != "$ivy") || ref.resolve() != null) return
    AmmoniteScriptWrappersHolder.getInstance(ref.getProject).registerProblemIn(ref.getContainingFile.asInstanceOf[ScalaFile])
    holder.registerProblem(ref, WorksheetBundle.message("ammonite.cannot.resolve.import"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null: TextRange,
      new CreateImportedLibraryQuickFix(ref))
  }
}

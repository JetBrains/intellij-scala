package org.jetbrains.plugins.scala.worksheet.ammonite

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement

/**
  * User: Dmitry.Naydanov
  * Date: 30.08.17.
  */
class AmmoniteUnresolvedLibraryInspection extends AbstractInspection("Unresolved Ivy import") {
  override protected def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case stableRef: ScStableCodeReferenceElement =>
      stableRef.qualifier match {
        case Some(x) => x.refName match {
          case "$ivy" =>
            if (stableRef.resolve() == null) {
              holder.registerProblem(stableRef, "Cannot resolve import", ProblemHighlightType.WEAK_WARNING, null: TextRange, 
                new CreateImportedLibraryQuickFix(stableRef))
            }
          case _ => 
        }
        case _ => 
      }
  }
}

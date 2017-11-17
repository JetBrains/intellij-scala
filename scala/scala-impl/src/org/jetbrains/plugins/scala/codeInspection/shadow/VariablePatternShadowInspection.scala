package org.jetbrains.plugins.scala
package codeInspection
package shadow

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createPatternFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor

class VariablePatternShadowInspection extends AbstractInspection("VariablePatternShadow", "Suspicious shadowing by a Variable Pattern") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case refPat: ScReferencePattern => check(refPat, holder)
  }

  private def check(refPat: ScReferencePattern, holder: ProblemsHolder) {
    val isInCaseClause = ScalaPsiUtil.nameContext(refPat).isInstanceOf[ScCaseClause]
    if (isInCaseClause) {
      val dummyRef: ScStableCodeReferenceElement = createReferenceFromText(refPat.name, refPat.getContext.getContext, refPat)
      
      if (dummyRef == null) return //can happen in invalid code, e.g. if ')' is absent in case pattern
      val proc = new ResolveProcessor(StdKinds.valuesRef, dummyRef, refPat.name)
      val results = dummyRef.asInstanceOf[ScStableCodeReferenceElement].doResolve(proc)
      def isAccessible(rr: ResolveResult): Boolean = rr.getElement match {
        case named: PsiNamedElement => proc.isAccessible(named, refPat)
        case _ => false
      }
      if (results.exists(isAccessible)) {
        holder.registerProblem(refPat.nameId, getDisplayName, new ConvertToStableIdentifierPatternFix(refPat), new RenameVariablePatternFix(refPat))
      }
    }
  }
}

class ConvertToStableIdentifierPatternFix(r: ScReferencePattern)
  extends AbstractFixOnPsiElement(s"Convert to Stable Identifier Pattern `${r.getText}`", r) {

  override protected def doApplyFix(ref: ScReferencePattern)
                                   (implicit project: Project): Unit = {
    val stableIdPattern = createPatternFromText("`%s`".format(ref.getText))
    ref.replace(stableIdPattern)
  }
}

class RenameVariablePatternFix(ref: ScReferencePattern) extends RenameElementQuickfix(ref, "Rename Variable Pattern")
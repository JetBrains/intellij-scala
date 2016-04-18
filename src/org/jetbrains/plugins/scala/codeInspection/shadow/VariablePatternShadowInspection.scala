package org.jetbrains.plugins.scala
package codeInspection
package shadow

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiNamedElement, ResolveResult}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ResolvableStableCodeReferenceElement, StdKinds}

class VariablePatternShadowInspection extends AbstractInspection("VariablePatternShadow", "Suspicious shadowing by a Variable Pattern") {

  def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case refPat: ScReferencePattern => check(refPat, holder)
  }

  private def check(refPat: ScReferencePattern, holder: ProblemsHolder)
                   (implicit typeSystem: TypeSystem = holder.typeSystem) {
    val isInCaseClause = ScalaPsiUtil.nameContext(refPat).isInstanceOf[ScCaseClause]
    if (isInCaseClause) {
      val dummyRef: ScStableCodeReferenceElement = ScalaPsiElementFactory.createReferenceFromText(refPat.name, refPat.getContext.getContext, refPat)
      
      if (dummyRef == null) return //can happen in invalid code, e.g. if ')' is absent in case pattern
      val proc = new ResolveProcessor(StdKinds.valuesRef, dummyRef, refPat.name)
      val results = dummyRef.asInstanceOf[ResolvableStableCodeReferenceElement].doResolve(dummyRef, proc)
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
        extends AbstractFixOnPsiElement("Convert to Stable Identifier Pattern `%s`".format(r.getText), r) {
  def doApplyFix(project: Project) {
    val ref = getElement
    val stableIdPattern = ScalaPsiElementFactory.createPatternFromText("`%s`".format(ref.getText), ref.getManager)
    ref.replace(stableIdPattern)
  }
}

class RenameVariablePatternFix(ref: ScReferencePattern) extends RenameElementQuickfix(ref, "Rename Variable Pattern")
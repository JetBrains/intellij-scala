package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.{LocalInspectionTool, LocalQuickFix, ProblemsHolder}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.codeInspection.quickfix.RenameElementQuickfix
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createPatternFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor

class VariablePatternShadowInspection extends LocalInspectionTool {

  import VariablePatternShadowInspection._

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case refPat: ScReferencePattern if isInCaseClause(refPat) && doesShadowOtherPattern(refPat) =>
      val quickFixes = Array[LocalQuickFix](
        new ConvertToStableIdentifierPatternFix(refPat),
        new RenameVariablePatternFix(refPat)
      )
      holder.registerProblem(refPat.nameId, description, quickFixes: _*)
    case _ =>
  }
}

object VariablePatternShadowInspection {
  def description: String = ScalaInspectionBundle.message("displayname.suspicious.shadowing.by.a.variable.pattern")

  def isInCaseClause(ref: ScReferencePattern): Boolean =
    ref.nameContext.is[ScCaseClause]

  def doesShadowOtherPattern(ref: ScReferencePattern): Boolean = (
    for {
      // createReferenceFromText might return null in invalid code, e.g. if ')' is absent in case pattern
      dummyRef <- Option(createReferenceFromText(ref.name, ref.getContext.getContext, ref))
      proc = new ResolveProcessor(StdKinds.valuesRef, dummyRef, ref.name)
      results = dummyRef.doResolve(proc)
    } yield results.exists(rr => proc.isAccessible(rr.getElement, ref))
    ).getOrElse(false)
}

class ConvertToStableIdentifierPatternFix(r: ScReferencePattern)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.stable.identifier.pattern", r.getText), r) {

  override protected def doApplyFix(ref: ScReferencePattern)
                                   (implicit project: Project): Unit = {
    val stableIdPattern = createPatternFromText(s"`${ref.getText}`", ref)
    ref.replace(stableIdPattern)
  }
}

class RenameVariablePatternFix(ref: ScReferencePattern) extends RenameElementQuickfix(ref, ScalaInspectionBundle.message("rename.variable.pattern"))
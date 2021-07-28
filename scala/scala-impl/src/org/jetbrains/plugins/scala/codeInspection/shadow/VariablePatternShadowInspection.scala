package org.jetbrains.plugins.scala
package codeInspection
package shadow

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemDescriptor, ProblemHighlightType}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createPatternFromText, createReferenceFromText}
import org.jetbrains.plugins.scala.lang.resolve.StdKinds
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor

class VariablePatternShadowInspection extends AbstractRegisteredInspection {
  import VariablePatternShadowInspection._

  override protected def problemDescriptor(element: PsiElement,
                                           maybeQuickFix: Option[LocalQuickFix],
                                           descriptionTemplate: String,
                                           highlightType: ProblemHighlightType)
                                          (implicit manager: InspectionManager, isOnTheFly: Boolean): Option[ProblemDescriptor] = {
    element match {
      case refPat: ScReferencePattern if isInCaseClause(refPat) && doesShadowOtherPattern(refPat) =>
        val quickFixes = Array[LocalQuickFix](
          new ConvertToStableIdentifierPatternFix(refPat),
          new RenameVariablePatternFix(refPat)
        )
        val descriptor =
          manager.createProblemDescriptor(refPat.nameId, description, isOnTheFly, quickFixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)

        Some(descriptor)
      case _ =>
        None
    }
  }
}

object VariablePatternShadowInspection {
  def description: String = ScalaInspectionBundle.message("suspicious.shadowing.by.a.variable.pattern")

  def isInCaseClause(ref: ScReferencePattern): Boolean =
    ScalaPsiUtil.nameContext(ref).is[ScCaseClause]

  def doesShadowOtherPattern(ref: ScReferencePattern): Boolean = (
    for {
      // createReferenceFromText might return null in invalid code, e.g. if ')' is absent in case pattern
      dummyRef <- Option(createReferenceFromText(ref.name, ref.getContext.getContext, ref))
      proc = new ResolveProcessor(StdKinds.valuesRef, dummyRef, ref.name)
      results = dummyRef.asInstanceOf[ScStableCodeReference].doResolve(proc)
    } yield results.exists(rr => proc.isAccessible(rr.getElement, ref))
  ).getOrElse(false)
}

class ConvertToStableIdentifierPatternFix(r: ScReferencePattern)
  extends AbstractFixOnPsiElement(ScalaInspectionBundle.message("convert.to.stable.identifier.pattern", r.getText), r) {

  override protected def doApplyFix(ref: ScReferencePattern)
                                   (implicit project: Project): Unit = {
    val stableIdPattern = createPatternFromText(s"`${ref.getText}`")
    ref.replace(stableIdPattern)
  }
}

class RenameVariablePatternFix(ref: ScReferencePattern) extends RenameElementQuickfix(ref, ScalaInspectionBundle.message("rename.variable.pattern"))
package org.jetbrains.plugins.scala.codeInspection.typeChecking

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.annotator.PatternAnnotatorUtil
import org.jetbrains.plugins.scala.codeInspection.typeChecking.PatternMayNeverMatchInspection.{ScPatternExpectedAndPatternType, inspectionId, inspectionName}
import org.jetbrains.plugins.scala.codeInspection.{AbstractInspection, InspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScPattern
import org.jetbrains.plugins.scala.lang.psi.types.ComparingUtil._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt, ScTypePresentation}
import org.jetbrains.plugins.scala.project.ProjectExt

/**
  * Author: Svyatoslav Ilinskiy
  * Date: 21.12.15.
  */
class PatternMayNeverMatchInspection extends AbstractInspection(inspectionId, inspectionName) {
  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case pat@ScPatternExpectedAndPatternType(exTp, patType) =>
      implicit val typeSystem = holder.getProject.typeSystem
      if (!PatternAnnotatorUtil.matchesPattern(exTp, patType) && !patType.conforms(exTp) &&
        !isNeverSubType(exTp, patType)) {
        //need to check so inspection highlighting doesn't interfere with PatterAnnotator's
        val message = PatternMayNeverMatchInspection.message(exTp, patType)
        holder.registerProblem(pat, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
  }
}

object PatternMayNeverMatchInspection {
  val inspectionId = "PatternMayNeverMatch"
  val inspectionName = InspectionBundle.message("pattern.may.never.match")
  def message(_expected: ScType, _found: ScType) = {
    val (expected, found) = ScTypePresentation.different(_expected, _found)
    InspectionBundle.message("pattern.may.never.match", expected, found)
  }
  
  object ScPatternExpectedAndPatternType {
    def unapply(pat: ScPattern): Option[(ScType, ScType)] = {
      (pat.expectedType, PatternAnnotatorUtil.patternType(pat)) match {
        case (Some(expected), Some(pattern)) => Option((expected, pattern))
        case _ => None
      }
    }
  }
}

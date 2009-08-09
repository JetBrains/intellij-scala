package org.jetbrains.plugins.scala
package codeInspection
package referenceInspections


import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder, LocalInspectionTool}
import com.intellij.psi.{PsiRecursiveElementVisitor, PsiElementVisitor, PsiNamedElement, PsiElement}
import lang.resolve.ScalaResolveResult
import java.lang.String
import lang.psi.api.base.ScReferenceElement
import lang.psi.api.base.types.ScTypeInferenceResult
import lang.psi.api.statements.ScTypeAliasDefinition

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

class CyclicReferencesInspection extends LocalInspectionTool {
  def getGroupDisplayName: String = InspectionsUtil.ERROR_HANDLING

  def getDisplayName: String = "Cyclic References Inspetion"

  def getShortName: String = "Cyclic References"

  override def runForWholeFile: Boolean = true

  override def getDefaultLevel: HighlightDisplayLevel = {
    HighlightDisplayLevel.ERROR
  }

  override def isEnabledByDefault: Boolean = true

  override def getStaticDescription: String =
    """This inspection reports all cyclic references, which lead to compile error."""

  override def getID: String = "Cyclic Referneces"


  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitReference(ref: ScReferenceElement) {
        check(ref, holder)
      }
    }
  }

  private def check(ref: ScReferenceElement, holder: ProblemsHolder) {
    ref.bind match {
      case Some(ScalaResolveResult(alias: ScTypeAliasDefinition, _)) => {
        alias.aliasedType match {
          case ScTypeInferenceResult(_, true, Some(ta)) => {
            holder.registerProblem(ref.nameId, ScalaBundle.message("cyclic.reference.type", ta.getName),
              ProblemHighlightType.LIKE_UNKNOWN_SYMBOL)
          }
          //todo check projection types
          case _ =>
        }
      }
      case _ =>
    }
  }
}
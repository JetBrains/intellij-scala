package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

/**
 * User: Alefas
 * Date: 06.02.12
 */
class TypeParameterShadowInspection extends AbstractInspection("TypeParameterShadowInspection", "Suspicious shadowing by a Type Parameter") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case refPat: ScTypeParam => check(refPat, holder)
  }
  
  private def isShadowing(refPat: ScTypeParam): Option[ScTypeParametersOwner] = {
    var parent: PsiElement = refPat.getParent
    val owner = refPat.owner
    while (parent != null) {
      parent match {
        case t: ScTypeParametersOwner if t != owner =>
          for (param <- t.typeParameters) {
            if (refPat.name == param.name && refPat.name != "_") return Some(t)
          }
        case _ =>
      }
      parent = parent.getParent
    }
    None
  }

  private def check(typeParam: ScTypeParam, holder: ProblemsHolder) {
    isShadowing(typeParam) match {
      case Some(_) =>
        holder.registerProblem(typeParam.nameId, getDisplayName + ": " + typeParam.name, new RenameTypeParameterFix(typeParam))
      case _ =>
    }
  }
}

class RenameTypeParameterFix(tp: ScTypeParam) extends RenameElementQuickfix(tp, "Rename Variable Pattern")
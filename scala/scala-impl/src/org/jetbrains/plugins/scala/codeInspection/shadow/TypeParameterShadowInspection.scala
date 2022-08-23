package org.jetbrains.plugins.scala.codeInspection.shadow

import com.intellij.codeInspection.{LocalInspectionTool, ProblemsHolder}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.quickfix.RenameElementQuickfix
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

import scala.annotation.unused

@unused("registered in scala-plugin-common.xml")
class TypeParameterShadowInspection extends LocalInspectionTool {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case e if e.isInScala3File => () // TODO Handle Scala 3 code (type lambdas, etc.), SCL-19723
    case refPat: ScTypeParam => check(refPat, holder)
    case _ =>
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

  private def check(typeParam: ScTypeParam, holder: ProblemsHolder): Unit = {
    isShadowing(typeParam) match {
      case Some(_) =>
        //noinspection ScalaExtractStringToBundle,ReferencePassedToNls
        holder.registerProblem(typeParam.nameId, getDisplayName + ": " + typeParam.name, new RenameTypeParameterFix(typeParam))
      case _ =>
    }
  }
}

class RenameTypeParameterFix(tp: ScTypeParam) extends RenameElementQuickfix(tp, ScalaInspectionBundle.message("rename.variable.pattern"))
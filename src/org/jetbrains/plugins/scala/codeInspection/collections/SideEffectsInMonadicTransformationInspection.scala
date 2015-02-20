package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.{PsiType, PsiPrimitiveType, PsiMethod, PsiElement}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.codeInspection.{InspectionBundle, AbstractInspection}
import org.jetbrains.plugins.scala.codeInspection.collections.SideEffectsInMonadicTransformationInspection._
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignStmt, ScInfixExpr, ScReferenceExpression, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScFunction, ScVariable}

/**
 * @author Nikolay.Tropin
 */
class SideEffectsInMonadicTransformationInspection extends OperationOnCollectionInspection {

  override def actionFor(holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case MethodRepr(_, _, Some(ref), Seq(arg))
      if monadicMethods.contains(ref.refName) &&
              OperationOnCollectionsUtil.checkResolve(ref, likeCollectionClasses) =>
      OperationOnCollectionsUtil.exprsWithSideEffect(arg).foreach {
        case expr => holder.registerProblem(expr, InspectionBundle.message("side.effects.in.monadic"), highlightType)
        case _ =>
      }
  }


  override def possibleSimplificationTypes: Array[SimplificationType] = Array.empty
}

object SideEffectsInMonadicTransformationInspection {
  val monadicMethods = Set("map", "flatMap", "filter", "withFilter")
}

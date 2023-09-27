package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.ProblemsHolder
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}

import scala.collection.immutable.ArraySeq

class SideEffectsInMonadicTransformationInspection extends OperationOnCollectionInspection {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case _ `.monadicMethod`(arg) =>
      exprsWithSideEffects(arg).foreach(
        expr => holder.registerProblem(expr, ScalaInspectionBundle.message("displayname.side.effects.in.a.monadic.transformation"))
      )
    case _ =>
  }

  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq.empty
}

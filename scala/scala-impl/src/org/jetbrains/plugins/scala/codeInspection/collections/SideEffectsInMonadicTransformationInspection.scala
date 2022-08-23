package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement

import scala.collection.immutable.ArraySeq

class SideEffectsInMonadicTransformationInspection extends OperationOnCollectionInspection {

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitorSimple = {
    case _ `.monadicMethod`(arg) =>
      exprsWithSideEffects(arg).foreach(
        expr => holder.registerProblem(expr, ScalaInspectionBundle.message("displayname.side.effects.in.a.monadic.transformation"), highlightType)
      )
    case _ =>
  }

  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq.empty
}

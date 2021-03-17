package org.jetbrains.plugins.scala
package codeInspection
package collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{StdType, StdTypes}

import scala.collection.immutable.ArraySeq

/**
 * @author Nikolay.Tropin
 */
class IfElseToOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: ArraySeq[SimplificationType] = ArraySeq(IfElseToOption)
}

object IfElseToOption extends SimplificationType {
  override def hint: String = ScalaInspectionBundle.message("hint.replace.with.option.expr")

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    val inner = expr match {
      case IfStmt(x `==` literal("null"), scalaNone(), scalaSome(x1))
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some((x, x1))
      case IfStmt(x `!=` literal("null"), scalaSome(x1), scalaNone())
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some((x, x1))
      case IfStmt(literal("null") `==` x, scalaNone(), scalaSome(x1))
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some((x, x1))
      case IfStmt(literal("null") `!=` x, scalaSome(x1), scalaNone())
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some((x, x1))
      case _ => None
    }
    val anyRef = StdTypes.instance(expr).AnyRef
    inner.filterNot {
      case (in, out) =>
        // check if the value would be converted into a value type before being given into Option(...)
        in.`type`().exists(_.conforms(anyRef)) &&
          out.`type`().exists(!_.conforms(anyRef))
    }.map { case (x, _) =>
      val text = x.getText
      replace(expr)
        .withText(s"Option($text)")
        .withHint(ScalaInspectionBundle.message("hint.replace.with.option.expr.with.preview", text))
        .highlightAll
    }
  }
}

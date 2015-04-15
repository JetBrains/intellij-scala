package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInsight.PsiEquivalenceUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression

/**
 * @author Nikolay.Tropin
 */
class IfElseToOptionInspection extends OperationOnCollectionInspection {
  override def possibleSimplificationTypes: Array[SimplificationType] = Array(IfElseToOption)
}

object IfElseToOption extends SimplificationType {
  override def hint: String = "Replace with Option(x)"

  override def getSimplification(expr: ScExpression): Option[Simplification] = {
    val inner = expr match {
      case IfStmt(x `==` literal("null"), scalaNone, scalaSome(x1))
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some(x)
      case IfStmt(x `!=` literal("null"), scalaSome(x1), scalaNone)
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some(x)
      case IfStmt(literal("null") `==` x, scalaNone, scalaSome(x1))
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some(x)
      case IfStmt(literal("null") `!=` x, scalaSome(x1), scalaNone)
        if PsiEquivalenceUtil.areElementsEquivalent(x, x1) =>
        Some(x)
      case _ => None
    }
    inner.map { x =>
      val text = x.getText
      replace(expr).withText(s"Option($text)").withHint(s"Replace with Option($text)").highlightAll
    }
  }
}

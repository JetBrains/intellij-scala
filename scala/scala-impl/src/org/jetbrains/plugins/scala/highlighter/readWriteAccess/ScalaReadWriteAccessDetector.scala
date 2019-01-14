package org.jetbrains.plugins.scala
package highlighter.readWriteAccess

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement

/**
 * User: Alexander Podkhalyuzin
 * Date: 06.10.2008
 */

class ScalaReadWriteAccessDetector extends ReadWriteAccessDetector {
  def getExpressionAccess(expression: PsiElement): Access = {
    expression match {
      case expression: ScExpression if ScalaReadWriteAccessDetector.isAccessedForWriting(expression) => Access.Write
      case _ => Access.Read
    }
  }
  def isReadWriteAccessible(element: PsiElement): Boolean = {
    element match {
      case x: PsiNamedElement =>
        x.isInstanceOf[ScalaPsiElement] && ScalaPsiUtil.nameContext(x) != null
      case _ => false
    }
  }
  def getReferenceAccess(referencedElement: PsiElement, reference: PsiReference): Access =
    getExpressionAccess(reference.getElement)

  def isDeclarationWriteAccess(element: PsiElement): Boolean = {
    element match {
      case x: PsiNamedElement =>
        ScalaPsiUtil.nameContext(x) match {
         case _: ScVariableDefinition | _: ScPatternDefinition => true
         case _ => false
       }
      case _ => false
    }
  }
}

private object ScalaReadWriteAccessDetector {
  def isAccessedForReading(expression: ScExpression): Boolean = !isAccessedForWriting(expression)

  //Now it's just inverse prev method
  def isAccessedForWriting(expression: ScExpression): Boolean = {
    expression.getParent match {
      case assign : ScAssignment if expression == assign.getLExpression => true
      case _ => false
    }
  }
}
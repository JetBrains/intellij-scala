package org.jetbrains.plugins.scala
package highlighter.readWriteAccess

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.{PsiElement, PsiReference, PsiNamedElement}
import lang.psi.api.expr.{ScAssignStmt, ScExpression}
import lang.psi.api.statements.{ScValue, ScVariableDefinition, ScPatternDefinition, ScVariable}
import lang.psi.ScalaPsiUtil

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
      case x: PsiNamedElement if ScalaPsiUtil.nameContext(x) != null => true
      case _ => false
    }
  }
  def getReferenceAccess(referencedElement: PsiElement, reference: PsiReference): Access =
    getExpressionAccess(reference.getElement)

  def isDeclarationWriteAccess(element: PsiElement): Boolean = {
    element match {
      case x: PsiNamedElement => {
         ScalaPsiUtil.nameContext(x) match {
           case _: ScVariableDefinition | _: ScPatternDefinition => true
           case _ => false
         }
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
      case assign : ScAssignStmt if expression == assign.getLExpression => true
      case _ => false
    }
  }
}
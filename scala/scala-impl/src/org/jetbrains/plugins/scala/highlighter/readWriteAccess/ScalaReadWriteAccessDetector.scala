package org.jetbrains.plugins.scala.highlighter.readWriteAccess

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector
import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector.Access
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScExpression, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition}

class ScalaReadWriteAccessDetector extends ReadWriteAccessDetector {
  override def getExpressionAccess(expression: PsiElement): Access = {
    expression match {
      case expression: ScExpression if ScalaReadWriteAccessDetector.isAccessedForWriting(expression) => Access.Write
      case _ => Access.Read
    }
  }
  override def isReadWriteAccessible(element: PsiElement): Boolean = {
    element match {
      case x: PsiNamedElement =>
        x.isInstanceOf[ScalaPsiElement] && x.nameContext != null
      case _ => false
    }
  }
  override def getReferenceAccess(referencedElement: PsiElement, reference: PsiReference): Access =
    getExpressionAccess(reference.getElement)

  override def isDeclarationWriteAccess(element: PsiElement): Boolean = {
    element match {
      case x: PsiNamedElement =>
        x.nameContext match {
         case _: ScVariableDefinition | _: ScPatternDefinition => true
         case _ => false
       }
      case _ => false
    }
  }
}

private object ScalaReadWriteAccessDetector {
  //Now it's just inverse prev method
  def isAccessedForWriting(expression: ScExpression): Boolean = {
    expression.getParent match {
      case assign : ScAssignment if expression == assign.leftExpression => true
      case ScSugarCallExpr(`expression`, method, _) if method.refName.endsWith("=") && method.bind().exists(_.name == method.refName.dropRight(1)) => true
      case _ => false
    }
  }
}
package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.TargetElementEvaluatorEx
import com.intellij.psi.{PsiFile, PsiElement, PsiReference}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

/**
 * Nikolay.Tropin
 * 9/2/13
 */
class ScalaTargetElementEvaluator extends TargetElementEvaluatorEx {

  def includeSelfInGotoImplementation(element: PsiElement): Boolean = true

  def getElementByReference(ref: PsiReference, flags: Int): PsiElement = ref.getElement match {
    case isUnapplyFromVal(binding) => binding
    case _ => null
  }

  private val isUnapplyFromVal = new {
    def unapply(ref: ScStableCodeReferenceElement): Option[(ScBindingPattern)] = {
      if (ref == null) return null
      ref.bind() match {
        case Some(resolve@ScalaResolveResult(fun: ScFunctionDefinition, _))
          if Set("unapply", "unapplySeq").contains(fun.name) =>
          resolve.innerResolveResult match {
            case Some(ScalaResolveResult(binding: ScBindingPattern, _)) => Some(binding)
            case _ => None
          }
        case _ => None
      }
    }
  }

  def isIdentifierPart(file: PsiFile, text: CharSequence, offset: Int): Boolean = {
    val child: PsiElement = file.findElementAt(offset)
    child != null && ScalaTokenTypes.IDENTIFIER_TOKEN_SET.contains(child.getNode.getElementType )
  }
}

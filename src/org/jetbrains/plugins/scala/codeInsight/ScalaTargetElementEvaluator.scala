package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.TargetElementEvaluatorEx
import com.intellij.psi.{PsiElement, PsiFile, PsiReference, PsiReferenceExpression}
import org.jetbrains.plugins.scala.extensions.Resolved
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper
import org.jetbrains.plugins.scala.lang.psi.light.scala._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * Nikolay.Tropin
 * 9/2/13
 */
class ScalaTargetElementEvaluator extends TargetElementEvaluatorEx {

  def includeSelfInGotoImplementation(element: PsiElement): Boolean = true

  def getElementByReference(ref: PsiReference, flags: Int): PsiElement = ref.getElement match {
    case isUnapplyFromVal(binding) => binding
    case isVarSetterFakeMethod(refPattern) => refPattern
    case isVarSetterWrapper(refPattern) => refPattern
    case Resolved(isLightScNamedElement(named), _) => named
    case _ => null
  }

  private object isUnapplyFromVal {
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

  private object isVarSetterFakeMethod {
    val setterSuffixes = Seq("_=", "_$eq")
    def unapply(ref: ScReferenceElement): Option[ScReferencePattern] = {
      ref.resolve() match {
        case fakeMethod: FakePsiMethod if setterSuffixes.exists(fakeMethod.getName.endsWith) =>
          fakeMethod.navElement match {
            case refPattern: ScReferencePattern if ScalaPsiUtil.nameContext(refPattern).isInstanceOf[ScVariable] => Some(refPattern)
            case _ => None
          }
        case _ => None
      }
    }
  }

  private object isVarSetterWrapper {
    val setterSuffix = "_$eq"
    def unapply(ref: PsiReferenceExpression): Option[ScReferencePattern] = {
      ref.resolve() match {
        case wrapper: PsiTypedDefinitionWrapper if wrapper.getName endsWith setterSuffix =>
          wrapper.typedDefinition match {
            case refPattern: ScReferencePattern if ScalaPsiUtil.nameContext(refPattern).isInstanceOf[ScVariable] => Some(refPattern)
            case _ => None
          }
        case _ => None
      }
    }
  }

  def isIdentifierPart(file: PsiFile, text: CharSequence, offset: Int): Boolean = {
    val child: PsiElement = file.findElementAt(offset)
    child != null && child.getNode != null && ScalaTokenTypes.IDENTIFIER_TOKEN_SET.contains(child.getNode.getElementType )
  }
}

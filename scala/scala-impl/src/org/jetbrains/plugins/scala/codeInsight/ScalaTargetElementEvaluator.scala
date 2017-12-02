package org.jetbrains.plugins.scala
package codeInsight

import java.util

import com.intellij.codeInsight.TargetElementEvaluatorEx
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.ResolvesTo
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReferenceElement, ScStableCodeReferenceElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
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
    case isCaseClassParameter(cp) => cp
    case isVarSetterFakeMethod(refPattern) => refPattern
    case isVarSetterWrapper(refPattern) => refPattern
    case ResolvesTo(isLightScNamedElement(named)) => named
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
        case FakePsiMethod(refPattern: ScReferencePattern)
          if setterSuffixes.exists(refPattern.getName.endsWith) && nameContext(refPattern).isInstanceOf[ScVariable] => Some(refPattern)
        case _ => None
      }
    }
  }

  private object isVarSetterWrapper {
    val setterSuffix = "_$eq"
    def unapply(ref: PsiReferenceExpression): Option[ScReferencePattern] = {
      ref.resolve() match {
        case PsiTypedDefinitionWrapper(refPattern: ScReferencePattern)
          if refPattern.getName.endsWith(setterSuffix) && nameContext(refPattern).isInstanceOf[ScVariable] => Some(refPattern)
        case _ => None
      }
    }
  }

  private object isCaseClassParameter {
    def unapply(ref: ScReferenceElement): Option[ScParameter] = {
      ref.resolve() match {
        case p: ScParameter =>
          p.owner match {
            case a: ScFunctionDefinition if a.isSyntheticApply =>
              a.containingClass match {
                case obj: ScObject =>
                  obj.fakeCompanionClassOrCompanionClass match {
                    case cl: ScClass if cl.isCase => return cl.parameters.find(_.name == p.name)
                    case _ =>
                  }
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }
      None
    }
  }

  def isIdentifierPart(file: PsiFile, text: CharSequence, offset: Int): Boolean = {
    val child: PsiElement = file.findElementAt(offset)
    child != null && child.getNode != null && ScalaTokenTypes.IDENTIFIER_TOKEN_SET.contains(child.getNode.getElementType )
  }

  private def addClassParameterForSyntheticApply(newName: String, allRenames: util.Map[PsiElement, String], namedElement: PsiNamedElement) {
    namedElement match {
      case p: ScParameter =>
        p.owner match {
          case a: ScFunctionDefinition if a.isSyntheticApply =>
            a.containingClass match {
              case obj: ScObject =>
                obj.fakeCompanionClassOrCompanionClass match {
                  case cl: ScClass if cl.isCase =>
                    cl.parameters.filter(_.name == p.name).foreach(allRenames.put(_, newName))
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
      case _ =>
    }
  }
}

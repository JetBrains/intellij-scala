package org.jetbrains.plugins.scala
package codeInsight

import com.intellij.codeInsight.{TargetElementEvaluatorEx, TargetElementEvaluatorEx2}
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.nameContext
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScReference, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

class ScalaTargetElementEvaluator extends TargetElementEvaluatorEx2 with TargetElementEvaluatorEx {

  override def getElementByReference(ref: PsiReference, flags: Int): PsiElement = ref.getElement match {
    case isUnapplyFromVal(binding) => binding
    case isCaseClassParameter(cp) => cp
    case isCaseClassApply(clazz) => clazz
    case isSyntheticObject(clazz) => clazz
    case isVarSetterFakeMethod(refPattern) => refPattern
    case isVarSetterWrapper(refPattern) => refPattern
    case _ => null
  }

  override def isAcceptableNamedParent(parent: PsiElement): Boolean = parent match {
    case _: ScNewTemplateDefinition => false
    case _ => true
  }

  private object isUnapplyFromVal {
    def unapply(ref: ScStableCodeReference): Option[ScBindingPattern] = {
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
    private val setterSuffixes: Seq[String] = Seq("_=", "_$eq")
    def unapply(ref: ScReference): Option[ScReferencePattern] = {
      ref.resolve() match {
        case fake @ FakePsiMethod(refPattern: ScReferencePattern)
          if setterSuffixes.exists(fake.getName.endsWith) && nameContext(refPattern).is[ScVariable] => Some(refPattern)
        case _ => None
      }
    }
  }

  private object isVarSetterWrapper {
    val setterSuffix = "_$eq"
    def unapply(ref: PsiReferenceExpression): Option[ScReferencePattern] = {
      ref.resolve() match {
        case PsiTypedDefinitionWrapper(refPattern: ScReferencePattern)
          if refPattern.name.endsWith(setterSuffix) && nameContext(refPattern).is[ScVariable] => Some(refPattern)
        case _ => None
      }
    }
  }

  private object isCaseClassParameter {
    def unapply(ref: ScReference): Option[ScParameter] = {
      ref.resolve() match {
        case p: ScParameter =>
          p.owner match {
            case a: ScFunctionDefinition if a.isApplyMethod && a.isSynthetic =>
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

  private object isCaseClassApply {
    def unapply(ref: ScReference): Option[ScClass] = {
      ref.resolve() match {
        case (fun: ScFunctionDefinition) && ContainingClass(obj: ScObject) if fun.isApplyMethod && fun.isSynthetic =>
          Option(obj.fakeCompanionClassOrCompanionClass)
            .collect { case cls: ScClass if cls.isCase => cls }
        case _ => None
      }
    }
  }

  private object isSyntheticObject {
    def unapply(ref: ScReference): Option[PsiClass] = {
      ref.resolve() match {
        case obj: ScObject if obj.isSyntheticObject => obj.baseCompanion
        case _ => None
      }
    }
  }

  override def isIdentifierPart(file: PsiFile, text: CharSequence, offset: Int): Boolean = {
    val child: PsiElement = file.findElementAt(offset)
    child != null && child.getNode != null && ScalaTokenTypes.IDENTIFIER_TOKEN_SET.contains(child.getNode.getElementType )
  }
}

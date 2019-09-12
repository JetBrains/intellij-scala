package org.jetbrains.plugins.scala.lang.completion
package ml

import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsMethodImpl
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScPackage
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScAssignment, ScCatchBlock, ScPostfixExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScPatternDefinition, ScTypeAlias, ScTypeAliasDefinition, ScTypedDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, PartialFunctionType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

import scala.annotation.tailrec

object PsiFeaturesUtil {

  private val PostfixPattern = identifierWithParentsPattern(
    classOf[ScReferenceExpression],
    classOf[ScPostfixExpr]
  )

  private val NameHolderClasses: Array[Class[_ <: PsiElement]] = Array(
    classOf[ScTypedDeclaration],
    classOf[ScTypeAliasDefinition],
    classOf[ScAssignment],
    classOf[ScPatternDefinition],
    classOf[ScVariableDefinition],
    classOf[ScFunctionDefinition],
    classOf[ScBindingPattern],
    classOf[ScParameter]
  )

  private val BaseExceptinons = Set(
    CommonClassNames.JAVA_LANG_EXCEPTION,
    CommonClassNames.JAVA_LANG_THROWABLE,
    CommonClassNames.JAVA_LANG_ERROR
  )

  def isJavaObjectMethod(maybeElement: Option[PsiElement]): Boolean = maybeElement.exists {
    case methodImpl: ClsMethodImpl =>
      Option(methodImpl.getContainingClass).exists(_.qualifiedNameOpt.contains(CommonClassNames.JAVA_LANG_OBJECT))
    case _ => false
  }

  def kind(maybeElement: Option[PsiElement]): Option[ItemKind] = {

    @tailrec
    def isException(psiClass: PsiClass, depth: Int): Boolean = {
      if (psiClass.qualifiedNameOpt.exists(BaseExceptinons)) true
      else if (depth > 0) {
        val parent = psiClass.getSuperClass
        if (parent != null) isException(parent, depth - 1) else false
      }
      else false
    }

    maybeElement.collectFirst {
      case c: PsiClass if isException(c, depth = 4) => ItemKind.EXCEPTION
      case _: ScPackage => ItemKind.PACKAGE
      case _: ScObject => ItemKind.OBJECT
      case _: ScTrait => ItemKind.TRAIT
      case _: ScClass => ItemKind.CLASS
      case _: ScTypeAlias => ItemKind.TYPE_ALIAS
      case _: ScPrimaryConstructor => ItemKind.CONSTRUCTOR
      case _: ScFunction => ItemKind.FUNCTION
      case _: ScSyntheticFunction => ItemKind.SYNTHETHIC_FUNCTION
      case f: ScFieldId if f.isVar => ItemKind.VARIABLE
      case _: ScFieldId => ItemKind.VALUE
      case r: ScReferencePattern if r.isVar => ItemKind.VARIABLE
      case r: ScReferencePattern if r.isVal => ItemKind.VALUE
      case _: ScReferencePattern => ItemKind.REFERENCE
      case _: PsiPackage => ItemKind.PACKAGE
      case c: PsiClass if c.isInterface => ItemKind.TRAIT
      case _: PsiClass => ItemKind.CLASS
      case _: PsiMethod => ItemKind.FUNCTION
      case f: PsiField if f.getModifierList.hasModifierProperty(PsiModifier.FINAL) => ItemKind.VALUE
      case _: PsiField => ItemKind.VARIABLE
    }
  }

  def argumentCount(maybeElement: Option[PsiElement]): Int = {
    val countOption = maybeElement.collectFirst {
      case function: ScFunction => function.parameters.size
      case method: PsiMethod => method.getParameterList.getParametersCount
      case syntheticFunction: ScSyntheticFunction => syntheticFunction.paramClauses.headOption.map(_.size).getOrElse(0)
      case Typeable(FunctionType(_, argumentTypes)) => argumentTypes.size
      case Typeable(PartialFunctionType(_, _)) => 1
    }

    countOption.getOrElse(-1)
  }

  def postfix(maybeElement: Option[PsiElement]): Boolean = maybeElement.exists(PostfixPattern.accepts)

  def insideCatch(maybeElement: Option[PsiElement]): Boolean =
    maybeElement.flatMap(_.parentOfType(classOf[ScCatchBlock])).nonEmpty

  def expectedName(maybeElement: Option[PsiElement]): Option[String] = {
    maybeElement.flatMap(namedHolder).flatMap {
      case typedDeclaration: ScTypedDeclaration => typedDeclaration.declaredElements.headOption.map(_.name)
      case typeAliasDefinition: ScTypeAliasDefinition => Option(typeAliasDefinition.name)
      case assignment: ScAssignment => assignment.referenceName
      case valDefinition: ScPatternDefinition => valDefinition.bindings.headOption.map(_.name)
      case varDefinition: ScVariableDefinition => varDefinition.bindings.headOption.map(_.name)
      case defDefinition: ScFunctionDefinition => Option(defDefinition.name)
      case bindingPattern: ScBindingPattern => Option(bindingPattern.name)
      case parameter: ScParameter => Option(parameter.name)
      case _ => None
    }
  }

  private def namedHolder(element: PsiElement): Option[PsiElement] = {
    val parent = PsiTreeUtil.getParentOfType(element, NameHolderClasses: _*)

    @tailrec
    def isRightAncestor(child: PsiElement, parent: PsiElement): Boolean = {
      val currentParrent = child.getParent
      if (currentParrent.getLastChild ne child) false
      else if (currentParrent eq parent) true
      else isRightAncestor(currentParrent, parent)
    }

    Option(parent).filter(isRightAncestor(element, _))
  }
}

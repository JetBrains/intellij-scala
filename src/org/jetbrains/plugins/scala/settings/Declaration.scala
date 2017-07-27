package org.jetbrains.plugins.scala.settings

import com.intellij.psi.{PsiElement, PsiModifierListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScAnnotationsHolder, ScFunction, ScValue}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
  * @author Pavel Fatin
  */
trait Declaration {
  def visibility: Visibility

  def isImplicit: Boolean

  def isConstant: Boolean

  def hasUnitType: Boolean

  def typeMatches(patterns: Set[String]): Boolean

  def isAnnotatedWith(annotations: Set[String]): Boolean
}

object Declaration {
  def apply(element: PsiElement): Declaration = new PhysycalDeclaration(element)

  def apply(element: PsiElement, newVisibility: Visibility): Declaration = new PhysycalDeclaration(element) {
    override def visibility: Visibility = newVisibility
  }

  def apply(visibility: Visibility = Visibility.Default,
            isImplicit: Boolean = false,
            isConstant: Boolean = false,
            hasUnitType: Boolean = false): Declaration =
    SyntheticDeclaration(visibility, isImplicit, isConstant, hasUnitType)

  private class PhysycalDeclaration(element: PsiElement) extends Declaration {
    override def visibility: Visibility = element match {
      case owner: ScModifierListOwner =>
        if (owner.hasModifierPropertyScala("private")) Visibility.Private
        else if (owner.hasModifierPropertyScala("protected")) Visibility.Protected
        else Visibility.Default
      case owner: PsiModifierListOwner =>
        if (owner.hasModifierProperty("public")) Visibility.Default
        else if (owner.hasModifierProperty("private")) Visibility.Private
        else Visibility.Protected
      case _ => Visibility.Default
    }

    override def isImplicit: Boolean = element match {
      case owner: ScModifierListOwner => owner.hasModifierPropertyScala("implicit")
      case _ => false
    }

    override def isConstant: Boolean = element match {
      case value: ScValue => value.hasModifierPropertyScala("final")
      case _ => false
    }

    override def hasUnitType: Boolean = element match {
      case f: ScFunction => f.hasUnitResultType
      case e: ScExpression => e.getType().exists(_.isUnit)
      case _ => false
    }

    override def typeMatches(patterns: Set[String]): Boolean = false

    override def isAnnotatedWith(annotations: Set[String]): Boolean = element match {
      case holder: ScAnnotationsHolder => annotations.exists(holder.hasAnnotation)
      case _ => false
    }
  }

  private case class SyntheticDeclaration(visibility: Visibility,
                                          isImplicit: Boolean,
                                          isConstant: Boolean,
                                          hasUnitType: Boolean) extends Declaration {
    override def typeMatches(patterns: Set[String]): Boolean = false

    override def isAnnotatedWith(annotations: Set[String]): Boolean = false
  }
}

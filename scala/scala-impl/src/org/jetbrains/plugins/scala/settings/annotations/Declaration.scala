package org.jetbrains.plugins.scala.settings.annotations

import com.intellij.psi.{PsiElement, PsiModifierListOwner}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable

import scala.util.matching.Regex

/**
  * @author Pavel Fatin
  */
trait Declaration {
  def entity: Entity

  def visibility: Visibility

  def isImplicit: Boolean

  def isConstant: Boolean

  def hasUnitType: Boolean
  
  def hasAccidentalStructuralType: Boolean

  def typeMatches(patterns: Set[String]): Boolean

  def isAnnotatedWith(annotations: Set[String]): Boolean
}

object Declaration {
  private val AsteriskPattern = new Regex("(.*)\\*(.*)")

  def apply(element: PsiElement): Declaration = new PhysycalDeclaration(element)

  def apply(element: PsiElement, newVisibility: Visibility): Declaration = new PhysycalDeclaration(element) {
    override def visibility: Visibility = newVisibility
  }

  def apply(visibility: Visibility = Visibility.Default,
            isImplicit: Boolean = false,
            isConstant: Boolean = false,
            hasUnitType: Boolean = false,
            hasStructuralType: Boolean = false): Declaration =
    SyntheticDeclaration(visibility, isImplicit, isConstant, hasUnitType, hasStructuralType)

  private class PhysycalDeclaration(element: PsiElement) extends Declaration {
    override def entity: Entity = element match {
      case _: ScValue => Entity.Value
      case _: ScVariable => Entity.Variable
      case _: ScParameter => Entity.Parameter
      case _: ScUnderscoreSection => Entity.UnderscoreParameter
      case _ => Entity.Method
    }

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
      case v: Typeable => v.`type`().exists(_.isUnit)
      case _ => false
    }

    override def typeMatches(patterns: Set[String]): Boolean = element match {
      case v: Typeable => v.`type`().exists(t => patterns.exists(matches(t, _)))
      case _ => false
    }

    override def isAnnotatedWith(annotations: Set[String]): Boolean = element match {
      case holder: ScAnnotationsHolder => annotations.exists(holder.hasAnnotation)
      case _ => false
    }

    override def hasAccidentalStructuralType: Boolean = element match {
      case Typeable(tpe @ ScCompoundType(comps, _, _)) if comps.nonEmpty =>
        !ScCompoundType(comps)(tpe.projectContext).conforms(tpe)
      case _ => false
    }
  }

  private def matches(t: ScType, pattern: String): Boolean = {
    val s = t.canonicalText.stripPrefix("_root_.")

    pattern match {
      case AsteriskPattern(prefix, suffix) =>
        s.length > prefix.length + suffix.length && s.startsWith(prefix) && s.endsWith(suffix)
      case plainText =>
        s == plainText
    }
  }

  private case class SyntheticDeclaration(visibility: Visibility,
                                          isImplicit: Boolean,
                                          isConstant: Boolean,
                                          hasUnitType: Boolean,
                                          hasAccidentalStructuralType: Boolean) extends Declaration {

    override def entity: Entity = Entity.Method

    override def typeMatches(patterns: Set[String]): Boolean = false

    override def isAnnotatedWith(annotations: Set[String]): Boolean = false
  }
}

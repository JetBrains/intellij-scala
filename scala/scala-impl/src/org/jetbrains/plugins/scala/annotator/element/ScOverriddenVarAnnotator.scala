package org.jetbrains.plugins.scala.annotator.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

object ScOverriddenVarAnnotator extends ElementAnnotator[ScTypedDefinition] {
  override def annotate(element: ScTypedDefinition, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (hasOverrideModifier(element) && isProhibitedOverriddenVar(element))
      holder.createErrorAnnotation(element, ScalaBundle.message("var.cannot.be.overridden"))

  private def isProhibitedOverriddenVar(elem: ScTypedDefinition): Boolean =
    if (isVar(elem.nameContext)) {
      val supers = findScNamedElementsInSupers(elem)
      if (supers.exists(isVar)) !isOverrideOfAbstract(supers) else false
    } else {
      !hasSetter(elem)
    }

  private def findScNamedElementsInSupers(elem: ScTypedDefinition): Seq[PsiElement] =
    elem.nameContext match {
      case m: ScMember =>
        m.containingClass.supers.collect {
          case t: ScTypeDefinition => t.allTermsByName(elem.name).collect {
            case term: ScNamedElement => term.nameContext
          }
        }.flatten
      case _ =>
        Seq.empty
    }

  private def isVar(elem: PsiElement): Boolean =
    elem match {
      case p: ScClassParameter => p.isVar
      case _: ScVariable       => true
      case _                   => false
    }

  private def isOverrideOfAbstract(supers: Seq[PsiElement]): Boolean =
    supers.exists {
      case v: ScVariable       => v.isAbstract
      case s: ScClassParameter => s.isAbstractMember
      case _                   => false
    }

  private def hasOverrideModifier(elem: ScTypedDefinition): Boolean =
    elem.nameContext match {
      case o: ScModifierListOwner => o.getModifierList.modifiers.contains(ScalaModifier.Override)
      case _                      => false
    }

  private def hasSetter(elem: ScTypedDefinition): Boolean =
    elem.nameContext match {
      case p: ScClassParameter =>
        p.paramType.exists { t => hasSetter(elem.name, t.typeElement.getText, p.containingClass) }
      case v: ScValueOrVariable =>
        v.typeElement.exists { t => hasSetter(elem.name, t.getText, v.containingClass) }
      case _ =>
        false
    }

  private def hasSetter(elemName: String, elemType: String, containingClass: ScTemplateDefinition): Boolean =
    containingClass.allFunctionsByName(elemName + "_=").exists {
      case m: ScFunction =>
        m.hasUnitResultType && m.parameters.size == 1 && m.parameters.head.typeElement.exists(_.textMatches(elemType))
      case _ =>
        false
    }
}

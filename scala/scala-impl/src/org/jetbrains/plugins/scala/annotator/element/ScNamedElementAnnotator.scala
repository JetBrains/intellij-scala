package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

object ScNamedElementAnnotator extends ElementAnnotator[ScNamedElement] {
  override def annotate(element: ScNamedElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (isVarOverride(element))
      holder.createErrorAnnotation(element, ScalaBundle.message("var.cannot.be.overriden"))

  private def isVarOverride(elem: ScNamedElement): Boolean =
    elem.nameContext match {
      case p: ScClassParameter if hasOverrideModifier(p) => isVarOverride(elem.name, p.containingClass, elemIsVar = p.isVar)
      case v: ScValue          if hasOverrideModifier(v) => isVarOverride(elem.name, v.containingClass, elemIsVar = false)
      case v: ScVariable       if hasOverrideModifier(v) => isVarOverride(elem.name, v.containingClass, elemIsVar = true)
      case _                                             => false
    }

  private def isVarOverride(elemName: String, containingClass: ScTemplateDefinition, elemIsVar: Boolean) : Boolean =
    containingClass
      .supers
      .collect { case t: ScTypeDefinition => t }
      .exists { typeDefinition =>
        typeDefinition
          .allTermsByName(elemName)
          .collect { case term: ScNamedElement => term.nameContext }
          .collect { case nameContext: ScMember => nameContext }
          .exists {
            case v: ScVariable       => !elemIsVar || !v.isAbstract
            case s: ScClassParameter => !elemIsVar || (s.isVar && !s.isAbstractMember)
            case _                   => false
          }
      }

  private def hasOverrideModifier(elem: ScModifierListOwner): Boolean =
    elem.getModifierList.modifiers.contains(ScalaModifier.Override)
}

package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps
import org.jetbrains.plugins.scala.codeInspection.ui.CompilerInspectionOptions.isCompilerOptionPresent

object ScVarOverrideAnnotator extends ElementAnnotator[ScNamedElement] {
  override def annotate(element: ScNamedElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (!isCompilerOptionPresent(element, "-Yoverride-vars") && isVarOverride(element))
      holder.createErrorAnnotation(element, ScalaBundle.message("var.overriden.by.val"))

  private def isVarOverride(elem: ScNamedElement): Boolean =
    elem.nameContext match {
      case p: ScClassParameter if isOverride(p) => isVarOverride(elem, p.containingClass)
      case v: ScValue          if isOverride(v) => isVarOverride(elem, v.containingClass)
      case _                                    => false
    }

  private def isOverride(elem: ScModifierListOwner): Boolean =
    elem.getModifierList.modifiers.contains(ScalaModifier.Override)

  private def isVarOverride(elem: ScNamedElement, containingClass: ScTemplateDefinition) : Boolean =
    containingClass
      .supers
      .collect { case t: ScTypeDefinition => t }
      .exists { typeDefinition =>
        typeDefinition
          .allTermsByName(elem.name)
          .collect { case term: ScNamedElement if !term.isEquivalentTo(elem) => term.nameContext }
          .collect { case nameContext: ScMember => nameContext }
          .exists {
            case _: ScVariable       => true
            case s: ScClassParameter => s.isVar
            case _                   => false
          }
      }
}

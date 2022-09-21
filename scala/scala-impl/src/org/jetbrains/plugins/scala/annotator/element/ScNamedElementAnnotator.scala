package org.jetbrains.plugins.scala.annotator.element

import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.util.EnumSet.EnumSetOps

object ScNamedElementAnnotator extends ElementAnnotator[ScNamedElement] {
  override def annotate(element: ScNamedElement, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    if (isProhibitedVarOverride(element))
      holder.createErrorAnnotation(element, ScalaBundle.message("var.cannot.be.overriden"))

  private def isProhibitedVarOverride(elem: ScNamedElement): Boolean =
    if (hasOverrideModifier(elem) && isOverrideOfVar(elem)) {
      if (isVar(elem)) {
        !isOverrideOfAbstract(elem)
      } else {
        !hasSetter(elem)
      }
    } else {
      false
    }

  private def isOverrideOfVar(elem: ScNamedElement) : Boolean = elem.nameContext match {
    case m: ScMember =>
      val clazz = m.containingClass
      clazz != null && clazz.supers.exists {
        case t: ScTypeDefinition =>
          t.allTermsByName(elem.name).exists {
            case term: ScNamedElement => isVar(term)
            case _                    => false
          }
        case _ => false
      }
    case _ => false
  }

  private def isOverrideOfAbstract(elem: ScNamedElement) : Boolean = elem.nameContext match {
    case m: ScMember =>
      val clazz = m.containingClass
      clazz != null && clazz.supers.exists {
        case t: ScTypeDefinition =>
          t.allTermsByName(elem.name).exists {
            case term: ScNamedElement => term.nameContext match {
              case v: ScVariable       => v.isAbstract
              case s: ScClassParameter => s.isAbstractMember
              case _                   => false
            }
            case _ => false
          }
        case _ => false
      }
    case _ => false
  }

  private def hasOverrideModifier(elem: ScNamedElement): Boolean = elem.nameContext match {
    case o: ScModifierListOwner => o.getModifierList.modifiers.contains(ScalaModifier.Override)
    case _                      => false
  }

  private def hasSetter(elem: ScNamedElement): Boolean = elem.nameContext match {
    case p: ScClassParameter =>
      p.paramType.exists { t => hasSetter(elem.name, t.typeElement.getText, p.containingClass) }
    case v: ScValueOrVariable =>
      v.typeElement.exists { t => hasSetter(elem.name, t.getText, v.containingClass) }
    case _ => false
  }

  private def hasSetter(elemName: String, elemType: String, @Nullable containingClass: ScTemplateDefinition): Boolean =
    containingClass != null && containingClass.allFunctionsByName(elemName + "_=").exists {
      case m: ScFunction =>
        m.hasUnitResultType && m.parameters.size == 1 && m.parameters.head.typeElement.exists(_.textMatches(elemType))
    }

  private def isVar(elem: ScNamedElement): Boolean = elem.nameContext match {
    case p: ScClassParameter => p.isVar
    case _: ScVariable       => true
    case _                   => false
  }
}

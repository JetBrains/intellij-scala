package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.PsiModifier
import org.jetbrains.plugins.scala.annotator.quickfix.ImplementMethodsQuickFix
import org.jetbrains.plugins.scala.annotator.quickfix.modifiers.AddModifierQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.overrideImplement.{ClassMember, ScalaOIUtil, ScalaTypedMember}

/**
  * Pavel Fatin
  */
object NeedsToBeAbstract extends AnnotatorPart[ScTemplateDefinition] {

  import ScalaOIUtil.{getMembersToImplement => members}

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = definition match {
    case _: ScNewTemplateDefinition | _: ScObject =>
    case _ if !typeAware || isAbstract(definition) =>
    case _ =>
      members(definition, withOwn = true).collectFirst {
        case member: ScalaTypedMember /* SCL-2887 */ => message(definition, member)
      }.foreach { message =>
        val annotation = holder.createErrorAnnotation(definition.nameId, message)
        createFixes(definition).foreach(annotation.registerFix)
      }
  }

  private def message(definition: ScTemplateDefinition,
                      member: ClassMember with ScalaTypedMember) = ScalaBundle.message(
    "member.implementation.required",
    kindOf(definition),
    definition.name,
    member.getText,
    member.getParentNodeDelegate.getText
  )

  private def createFixes(definition: ScTemplateDefinition) = {
    val maybeModifierFix = definition match {
      case owner: ScModifierListOwner => Some(new AddModifierQuickFix(owner, PsiModifier.ABSTRACT))
      case _ => None
    }

    val maybeImplementFix = if (members(definition).nonEmpty) Some(new ImplementMethodsQuickFix(definition))
    else None

    maybeModifierFix ++ maybeImplementFix
  }
}
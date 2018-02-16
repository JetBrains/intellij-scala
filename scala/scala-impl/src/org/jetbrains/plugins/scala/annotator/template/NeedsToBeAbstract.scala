package org.jetbrains.plugins.scala.annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.annotator.quickfix.ImplementMethodsQuickFix
import org.jetbrains.plugins.scala.annotator.quickfix.modifiers.AddModifierQuickFix
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.overrideImplement.{ScalaOIUtil, ScalaTypedMember}

/**
  * Pavel Fatin
  */
object NeedsToBeAbstract extends AnnotatorPart[ScTemplateDefinition] {
  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    if (!typeAware) return

    if (definition.isInstanceOf[ScNewTemplateDefinition]) return

    if (definition.isInstanceOf[ScObject]) return

    if (isAbstract(definition)) return

    import ScalaOIUtil._
    val undefined = for {
      member <- getMembersToImplement(definition, withOwn = true)
      if member.isInstanceOf[ScalaTypedMember] // See SCL-2887
    } yield (member.getText, member.getParentNodeDelegate.getText)

    if (undefined.nonEmpty) {
      val annotation = holder.createErrorAnnotation(definition.nameId,
        message(kindOf(definition), definition.name, undefined.iterator.next))
      definition match {
        case owner: ScModifierListOwner => annotation.registerFix(new AddModifierQuickFix(owner, "abstract"))
        case _ =>
      }
      if (getMembersToImplement(definition).nonEmpty) {
        annotation.registerFix(new ImplementMethodsQuickFix(definition))
      }
    }
  }

  def message(kind: String, name: String, member: (String, String)): String = {
    val (first, second) = member
    s"$kind '$name' must either be declared abstract or implement abstract member '$first' in '$second'"
  }
}
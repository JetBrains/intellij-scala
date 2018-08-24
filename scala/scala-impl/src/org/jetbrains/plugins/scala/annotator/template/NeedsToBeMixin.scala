package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.{PsiMethod, PsiModifier}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature

/**
  * @author Alefas
  * @since 17.10.12
  */
object NeedsToBeMixin extends AnnotatorPart[ScTemplateDefinition] {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    if (definition.isInstanceOf[ScTrait]) return
    val signatures = TypeDefinitionMembers.getSignatures(definition)
      .allFirstSeq()
      .flatMap {
        _.map(_._2)
      }

    for (signature <- signatures) {
      signature.info match {
        case PhysicalSignature(function: ScFunctionDefinition, _) if isOverrideAndAbstract(function) =>
          val flag = signature.supers.map(_.info.namedElement).forall {
            case f: ScFunctionDefinition => isOverrideAndAbstract(f)
            case _: ScBindingPattern => true
            case m: PsiMethod => m.hasModifierProperty(PsiModifier.ABSTRACT)
            case _ => true
          }

          for {
            place <- definition match {
              case _ if !flag => None
              case typeDefinition: ScTypeDefinition => Some(typeDefinition.nameId)
              case templateDefinition: ScNewTemplateDefinition =>
                templateDefinition.extendsBlock.templateParents
                  .flatMap(_.typeElements.headOption)
              case _ => None
            }

            message = ScalaBundle.message(
              "mixin.required",
              kindOf(definition),
              definition.name,
              function.name,
              function.containingClass.name
            )
          } holder.createErrorAnnotation(place, message)
        case _ => //todo: vals?
      }
    }
  }

  private def isOverrideAndAbstract(definition: ScFunctionDefinition) =
    definition.hasModifierPropertyScala(PsiModifier.ABSTRACT) &&
      definition.hasModifierPropertyScala("override")
}

package org.jetbrains.plugins.scala
package annotator.template

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunctionDefinition, ScPatternDefinition, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature

/**
 * @author Alefas
 * @since 17.10.12
 */
object NeedsToBeMixin extends AnnotatorPart[ScTemplateDefinition] {
  def annotate(element: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    if (element.isInstanceOf[ScTrait]) return
    val signaturesIterator = TypeDefinitionMembers.getSignatures(element).allFirstSeq().
      flatMap(_.map(_._2)).iterator

    while (signaturesIterator.hasNext) {
      val signature = signaturesIterator.next()
      signature.info match {
        case sign: PhysicalSignature =>
          val m = sign.method
          m match {
            case f: ScFunctionDefinition =>
              if (f.hasModifierPropertyScala("abstract") && f.hasModifierPropertyScala("override")) {
                signature.supers.find {
                  case node => node.info.namedElement match {
                    case f: ScFunctionDefinition => !f.hasModifierPropertyScala("abstract") ||
                            !f.hasModifierProperty("override")
                    case v: ScBindingPattern =>
                      v.nameContext match {
                        case _: ScVariableDefinition if !f.hasModifierPropertyScala("abstract") ||
                          !f.hasModifierPropertyScala("override") => true
                        case _: ScPatternDefinition if !f.hasModifierPropertyScala("abstract") ||
                          !f.hasModifierPropertyScala("override") => true
                        case _ => false
                      }
                    case m: PsiMethod => !m.hasModifierProperty("abstract")
                    case _ => false
                  }
                } match {
                  case Some(_) => //do nothing
                  case None =>
                    val place: PsiElement = element match {
                      case td: ScTypeDefinition => td.nameId
                      case t: ScNewTemplateDefinition =>
                        t.extendsBlock.templateParents.flatMap(_.typeElements.headOption).orNull
                      case _ => null
                    }
                    if (place != null) {
                      holder.createErrorAnnotation(place,
                        message(kindOf(element), element.name, (f.name, f.containingClass.name)))
                    }
                }
              }
            case _ =>
          }
        case _ => //todo: vals?
      }
    }
  }

  def message(kind: String, name: String, member: (String, String)): String = {
    s"$kind '$name' needs to be mixin, since member '${member._1}' in '${member._2}' is marked 'abstract' and 'override', " +
      s"but no concrete implementation could be found in a base class"
  }
}

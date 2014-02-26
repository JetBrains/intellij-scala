package org.jetbrains.plugins.scala
package annotator.template

import annotator.AnnotatorPart
import lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScTrait, ScTemplateDefinition}
import com.intellij.lang.annotation.AnnotationHolder
import lang.psi.types.PhysicalSignature
import lang.psi.api.statements.{ScPatternDefinition, ScVariableDefinition, ScFunctionDefinition}
import lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import lang.psi.api.base.patterns.ScBindingPattern
import com.intellij.psi.{PsiMethod, PsiElement}
import lang.psi.api.expr.ScNewTemplateDefinition

/**
 * @author Alefas
 * @since 17.10.12
 */
object NeedsToBeMixin extends AnnotatorPart[ScTemplateDefinition] {
  def kind: Class[ScTemplateDefinition] = classOf[ScTemplateDefinition]

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
                    case Some(f: ScFunctionDefinition) => !f.hasModifierPropertyScala("abstract") ||
                            !f.hasModifierProperty("override")
                    case Some(v: ScBindingPattern) =>
                      v.nameContext match {
                        case v: ScVariableDefinition if !f.hasModifierPropertyScala("abstract") ||
                          !f.hasModifierPropertyScala("override") => true
                        case v: ScPatternDefinition if !f.hasModifierPropertyScala("abstract") ||
                          !f.hasModifierPropertyScala("override") => true
                        case _ => false
                      }
                    case Some(m: PsiMethod) => !m.hasModifierProperty("abstract")
                    case _ => false
                  }
                } match {
                  case Some(_) => //do nothing
                  case None =>
                    val place: PsiElement = element match {
                      case td: ScTypeDefinition => td.nameId
                      case t: ScNewTemplateDefinition =>
                        t.extendsBlock.templateParents.flatMap(_.typeElements.headOption).getOrElse(null)
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

  def message(kind: String, name: String, member: (String, String)) = {
    s"$kind '$name' needs to be mixin, since member '${member._1}' in '${member._2}' is marked 'abstract' and 'override', " +
      s"but no concrete implementation could be found in a base class"
  }
}

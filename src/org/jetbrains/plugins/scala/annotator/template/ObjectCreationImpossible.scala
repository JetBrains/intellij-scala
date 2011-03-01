package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.annotator.quickfix.ImplementMethodsQuickFix

/**
 * Pavel Fatin
 */

object ObjectCreationImpossible extends AnnotatorPart[ScTemplateDefinition] {
  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, typeAware: Boolean) {
    val isNew = definition.isInstanceOf[ScNewTemplateDefinition]
    val isObject = definition.isInstanceOf[ScObject]

    if (!isNew && !isObject) return

    val refs = definition.refs

    val hasBody = definition.extendsBlock.templateBody.isDefined
    val hasAbstract = refs.flatMap(_._2.toSeq).exists(isAbstract)

    if(hasAbstract) {
      refs.headOption.foreach {
        case (refElement, Some(psiClass)) => {
          val toImplement = ScalaOIUtil.getMembersToImplement(definition)
          val members = ScalaOIUtil.toMembers(toImplement)
          val undefined = members.map(it => (it.getText, it.getParentNodeDelegate.getText))

          if(!undefined.isEmpty) {
            val element = if(isNew) refElement else definition.asInstanceOf[ScObject].nameId
            val annotation = holder.createErrorAnnotation(element, message(undefined: _*))
            annotation.registerFix(new ImplementMethodsQuickFix(definition))
          }
        }
        case _ =>
      }
    }
  }

  def message(members: (String, String)*) = {
    "Object creation impossible, since %s".format(
      members.map(p => " member %s in %s is not defined".format(p._1, p._2)).mkString("; "))
  }
}

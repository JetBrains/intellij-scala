package org.jetbrains.plugins.scala.annotator.template

import org.jetbrains.plugins.scala.annotator.AnnotatorPart
import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.overrideImplement.ScalaOIUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}

/**
 * Pavel Fatin
 */

object NeedsToBeAbstract extends AnnotatorPart[ScTemplateDefinition] {
  def kind = classOf[ScTemplateDefinition]

  def annotate(definition: ScTemplateDefinition, holder: AnnotationHolder, advanced: Boolean) {
    if (definition.isInstanceOf[ScNewTemplateDefinition]) return

    if (definition.isInstanceOf[ScObject]) return

    if(isAbstract(definition)) return

    val members = ScalaOIUtil.toMembers(ScalaOIUtil.getMembersToImplement(definition, true))
    val undefined = members.map(it => (it.getText, it.getParentNodeDelegate.getText))

    if(!undefined.isEmpty) {
      holder.createErrorAnnotation(definition.nameId,
        message(kindOf(definition), definition.getName, undefined: _*))
    }
  }

  def message(kind: String, name: String, members: (String, String)*) = {
    "%s %s needs to be abstract, since %s".format(kind, name,
      members.map(p => " member %s in %s is not defined".format(p._1, p._2)).mkString("; "))
  }
}
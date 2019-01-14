package org.jetbrains.plugins.scala
package annotator
package template

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}

/**
 * Pavel Fatin
 */

object UndefinedMember extends TemplateDefinitionAnnotatorPart {

  def annotate(definition: ScTemplateDefinition,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit = {
    val isNew = definition.isInstanceOf[ScNewTemplateDefinition]
    val isObject = definition.isInstanceOf[ScObject]

    if (!isNew && !isObject) return

    definition.physicalExtendsBlock.members.foreach {
      case _: ScTypeAliasDeclaration => // abstract type declarations are allowed
      case declaration: ScDeclaration => 
        val isNative = declaration match {
          case a: ScAnnotationsHolder => a.hasAnnotation("scala.native")
          case _ => false
        }
        if (!isNative) holder.createErrorAnnotation(declaration, ScalaBundle.message("illegal.undefined.member"))
      case _ =>
    }
  }
}
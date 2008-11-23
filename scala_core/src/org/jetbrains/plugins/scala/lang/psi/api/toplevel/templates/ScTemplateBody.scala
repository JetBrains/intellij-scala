package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates

import base.types.ScSelfTypeElement
import statements.{ScFunction, ScDeclaredElementsHolder, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import toplevel.typedef.ScMember
import api.toplevel.typedef._
/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:36
*/

trait ScTemplateBody extends ScalaPsiElement {
  def members = findChildrenByClass(classOf[ScMember])

  def holders = findChildrenByClass(classOf[ScDeclaredElementsHolder])

  def functions = findChildrenByClass(classOf[ScFunction])

  def aliases = findChildrenByClass(classOf[ScTypeAlias])

  def typeDefinitions: Seq[ScTypeDefinition] = findChildrenByClass(classOf[ScTypeDefinition])

  def selfTypeElement() = findChild(classOf[ScSelfTypeElement])
}
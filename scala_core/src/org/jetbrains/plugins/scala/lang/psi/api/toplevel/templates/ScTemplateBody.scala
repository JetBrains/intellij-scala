package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:36
*/

trait ScTemplateBody extends ScalaPsiElement {
  def members = findChildrenByClass(classOf[ScMember])

  def functions = findChildrenByClass(classOf[ScFunction])

  /**
   * Inner type definitions array
   * @return inner classes objects and traits
   */
  def typeDefinitions: Seq[ScTypeDefinition]
}
package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:38:36
*/

trait ScTemplateBody extends ScalaPsiElement {
  def members : Seq[ScMember]

  /**
   * Inner type definitions array
   * @return inner classes objects and traits
   */
  def typeDefinitions: Seq[ScTypeDefinition]
}
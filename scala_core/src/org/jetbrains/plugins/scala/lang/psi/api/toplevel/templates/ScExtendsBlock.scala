package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates

import psi.ScalaPsiElement
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScExtendsBlock extends ScalaPsiElement {

  def templateBody: Option[ScTemplateBody]


  /*
   * Return true if extends block is empty
   * @return is block empty
   */
  def empty: Boolean

  def templateParents = findChild(classOf[ScTemplateParents])

  def earlyDefinitions = findChild(classOf[ScEarlyDefinitions])

  def superTypes : Seq[ScType]

  def isAnonymousClass: Boolean
}
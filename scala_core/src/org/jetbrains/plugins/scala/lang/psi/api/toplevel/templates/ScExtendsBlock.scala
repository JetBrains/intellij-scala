package org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates

import com.intellij.psi.PsiClass
import psi.ScalaPsiElement
import typedef.{ScTypeDefinition, ScMember}
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScExtendsBlock extends ScalaPsiElement {

  def members : Seq[ScMember]
  
  def templateBody: Option[ScTemplateBody]


  /*
   * Return true if extends block is empty
   * @return is block empty
   */
  def empty: Boolean

  def templateParents = findChild(classOf[ScTemplateParents])

  def earlyDefinitions = findChild(classOf[ScEarlyDefinitions])

  def typeDefinitions : Seq[ScTypeDefinition]

  def superTypes : Seq[ScType]

  def supers : Seq[PsiClass]

  def isAnonymousClass: Boolean
}
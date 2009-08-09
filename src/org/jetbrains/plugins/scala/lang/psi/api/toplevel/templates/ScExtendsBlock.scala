package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import base.types.ScSelfTypeElement
import com.intellij.psi.PsiClass
import psi.ScalaPsiElement
import statements.{ScFunction, ScTypeAlias}
import typedef.{ScTypeDefinition, ScMember, ScTemplateDefinition}
import types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScExtendsBlock extends ScalaPsiElement {

  def members : Seq[ScMember]
  def functions : Seq[ScFunction]
  def aliases : Seq[ScTypeAlias]

  def templateBody: Option[ScTemplateBody]


  /*
   * Return true if extends block is empty
   * @return is block empty
   */
  def empty: Boolean

  def templateParents: Option[ScTemplateParents]

  def earlyDefinitions: Option[ScEarlyDefinitions]

  def typeDefinitions : Seq[ScTypeDefinition]

  def superTypes : List[ScType]

  def supers : Seq[PsiClass]

  def isAnonymousClass: Boolean

  def directSupersNames: Seq[String]

  def selfTypeElement() : Option[ScSelfTypeElement]

  def selfType() : Option[ScType]
  
  def isUnderCaseClass: Boolean


}
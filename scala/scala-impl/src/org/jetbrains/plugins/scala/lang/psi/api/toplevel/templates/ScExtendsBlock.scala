package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/** 
* @author Alexander Podkhalyuzin
* Date: 20.02.2008
*/

trait ScExtendsBlock extends ScalaPsiElement {

  def members : collection.Seq[ScMember]

  def functions: collection.Seq[ScFunction]

  def properties: collection.Seq[ScValueOrVariable]

  def aliases: collection.Seq[ScTypeAlias]

  def templateBody: Option[ScTemplateBody]


  /*
   * Return true if extends block is empty
   * @return is block empty
   */
  def empty: Boolean

  def templateParents: Option[ScTemplateParents]

  def earlyDefinitions: Option[ScEarlyDefinitions]

  def typeDefinitions : collection.Seq[ScTypeDefinition]

  def superTypes : List[ScType]

  def supers : collection.Seq[PsiClass]

  def isAnonymousClass: Boolean

  def selfTypeElement : Option[ScSelfTypeElement]

  def selfType : Option[ScType]
  
  def isUnderCaseClass: Boolean

  def addEarlyDefinitions(): ScEarlyDefinitions

}

object ScExtendsBlock {

  object EarlyDefinitions {
    def unapply(block: ScExtendsBlock): Option[ScEarlyDefinitions] = block.earlyDefinitions
  }
  object TemplateBody {
    def unapply(block: ScExtendsBlock): Option[ScTemplateBody] = block.templateBody
  }
}
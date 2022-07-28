package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package templates

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCases, ScExtension, ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

trait ScExtendsBlock extends ScalaPsiElement {

  def members: Seq[ScMember]

  def functions: Seq[ScFunction]

  def properties: Seq[ScValueOrVariable]

  def aliases: Seq[ScTypeAlias]

  def cases: Seq[ScEnumCases]

  def templateBody: Option[ScTemplateBody]

  /*
   * Return true if extends block is empty
   * @return is block empty
   */
  def empty: Boolean

  def templateParents: Option[ScTemplateParents]

  def derivesClause: Option[ScDerivesClause]

  def earlyDefinitions: Option[ScEarlyDefinitions]

  def typeDefinitions: Seq[ScTypeDefinition]

  def superTypes: List[ScType]

  def supers: Seq[PsiClass]

  def isAnonymousClass: Boolean

  def selfTypeElement: Option[ScSelfTypeElement]

  def extensions: Seq[ScExtension]

  def selfType: Option[ScType]

  def isUnderCaseClass: Boolean

  def isEnumDefinition: Boolean

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
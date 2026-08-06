package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package templates

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
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

  def getOrCreateTemplateBody: ScTemplateBody

  /*
   * Return true if extends block is empty
   * @return is block empty
   */
  def empty: Boolean

  def templateParents: Option[ScTemplateParents]

  def derivesClause: Option[ScDerivesClause]

  def earlyDefinitions: Option[ScEarlyDefinitions]

  def typeDefinitions: Seq[ScTypeDefinition]

  /**
   * Returns the effective direct parent types of the enclosing template.
   *
   * For `class Child extends Parent with Mixin` it returns the types for `Parent` and `Mixin`,
   * but not the parents of `Parent` or `Mixin`. The result also includes parents synthesized by the language or the plugin,
   * such as case-class parents and the implicit root class when needed.
   */
  def superTypes: List[ScType]

  /**
   * Returns the resolved classes for the effective direct parent types of the enclosing template.
   *
   * For `class Child extends Parent with Mixin`, returns the classes for `Parent` and `Mixin`,
   * but not an ancestor such as `Grandparent` of `Parent`.
   * Types which cannot be resolved to a class are omitted; synthesized direct parents are included.
   */
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

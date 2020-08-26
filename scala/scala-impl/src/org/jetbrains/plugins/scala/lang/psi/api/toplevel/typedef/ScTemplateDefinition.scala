package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.psi._
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author ven
 */
trait ScTemplateDefinition extends ScNamedElement with PsiClassAdapter with Typeable {

  def qualifiedName: String = null

  def originalElement: Option[ScTemplateDefinition]

  def setOriginal(actualElement: ScTypeDefinition): this.type

  // designates that this very element has been created as a result of macro transform
  // do not confuse with desugaredElement
  def isDesugared: Boolean = originalElement.isDefined

  def desugaredElement: Option[ScTemplateDefinition] = None

  def targetToken: LeafPsiElement

  def physicalExtendsBlock: ScExtendsBlock

  def extendsBlock: ScExtendsBlock

  def showAsInheritor: Boolean = extendsBlock.templateBody.isDefined

  def getTypeWithProjections(thisProjections: Boolean = false): TypeResult

  def functions: collection.Seq[ScFunction] = extendsBlock.functions

  def properties: collection.Seq[ScValueOrVariable] = extendsBlock.properties

  def aliases: collection.Seq[ScTypeAlias] = extendsBlock.aliases

  def members: collection.Seq[ScMember] = extendsBlock.members

  def typeDefinitions: collection.Seq[ScTypeDefinition] = extendsBlock.typeDefinitions

  def syntheticMethods: collection.Seq[ScFunction] = Seq.empty

  def syntheticTypeDefinitions: collection.Seq[ScTypeDefinition] = Seq.empty

  def syntheticMembers: collection.Seq[ScMember] = Seq.empty

  def selfTypeElement: Option[ScSelfTypeElement]

  def selfType: Option[ScType] = extendsBlock.selfType

  def superTypes: List[ScType] = extendsBlock.superTypes

  def supers: collection.Seq[PsiClass] = extendsBlock.supers

  def allTypeSignatures: Iterator[TypeSignature]

  def allVals: Iterator[TermSignature]

  def allMethods: Iterator[PhysicalMethodSignature]

  def allSignatures: Iterator[TermSignature]

  def isScriptFileClass: Boolean

  def addMember(member: ScMember, anchor: Option[PsiElement]): ScMember

  def deleteMember(member: ScMember): Unit

  def allFunctionsByName(name: String): Iterator[PsiMethod]

  def allTermsByName(name: String): Seq[PsiNamedElement]
}

object ScTemplateDefinition {
  object ExtendsBlock {
    def unapply(definition: ScTemplateDefinition): Some[ScExtendsBlock] = Some(definition.extendsBlock)
  }

  implicit class SyntheticMembersExt(private val td: ScTemplateDefinition) extends AnyVal {
    //this method is not in the ScTemplateDefinition trait to avoid binary incompatible change
    def membersWithSynthetic: collection.Seq[ScMember] =
      td.members ++ td.syntheticMembers ++ td.syntheticMethods ++ td.syntheticTypeDefinitions

  }

}

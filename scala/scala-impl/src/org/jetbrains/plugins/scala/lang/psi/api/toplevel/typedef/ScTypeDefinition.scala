package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Iconable
import com.intellij.psi._
import com.intellij.psi.impl.PsiClassImplUtil
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createObjectWithContext, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import scala.collection.Seq

/**
 * @author AlexanderPodkhalyuzin
 */

trait ScTypeDefinition extends ScTemplateDefinition with ScMember
    with NavigationItem with PsiClassAdapter with ScTypeParametersOwner with Iconable with ScDocCommentOwner
    with ScCommentOwner {

  def additionalJavaClass: Option[PsiClass] = None

  def isCase: Boolean = false

  def isObject: Boolean = false

  def isTopLevel: Boolean = !this.parentsInFile.exists(_.isInstanceOf[ScTypeDefinition])

  def getPath: String = {
    val qualName = qualifiedName
    val index = qualName.lastIndexOf('.')
    if (index < 0) "" else qualName.substring(0, index)
  }

  def getQualifiedNameForDebugger: String

  def signaturesByName(name: String): Seq[PhysicalSignature]

  def isPackageObject = false

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypeDefinition(this)
  }

  def getObjectClassOrTraitToken: PsiElement

  def getSourceMirrorClass: PsiClass

  override def isEquivalentTo(another: PsiElement): Boolean = {
    PsiClassImplUtil.isClassEquivalentTo(this, another)
  }

  def allInnerTypeDefinitions: Seq[ScTypeDefinition] = members.collect {
    case td: ScTypeDefinition => td
  }

  override def typeParameters: Seq[ScTypeParam]

  override def syntheticTypeDefinitionsImpl: Seq[ScTypeDefinition] = SyntheticMembersInjector.injectInners(this)

  override def syntheticMembersImpl: Seq[ScMember] = SyntheticMembersInjector.injectMembers(this)

  override protected def syntheticMethodsImpl: Seq[ScFunction] = SyntheticMembersInjector.inject(this)

  def fakeCompanionModule: Option[ScObject]

  override def showAsInheritor: Boolean = true

  def baseCompanionModule: Option[ScTypeDefinition]
}

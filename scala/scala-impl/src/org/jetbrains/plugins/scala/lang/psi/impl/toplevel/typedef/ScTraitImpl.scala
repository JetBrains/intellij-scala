package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScNamedBeginImpl
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12

import javax.swing.Icon

final class ScTraitImpl(stub: ScTemplateDefinitionStub[ScTrait],
                        nodeType: ScTemplateDefinitionElementType[ScTrait],
                        node: ASTNode,
                        debugName: String)
  extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScTrait
    with ScTypeParametersOwner
    with ScNamedBeginImpl {

  override def additionalClassJavaName: Option[String] = Option(getName).map(withSuffix)

  import com.intellij.psi._
  import com.intellij.psi.scope.PsiScopeProcessor

  override protected def targetTokenType: ScalaTokenType = ScalaTokenType.TraitKeyword

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                                  state: ResolveState,
                                                  lastParent: PsiElement,
                                                  place: PsiElement): Boolean = {
    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place) &&
      super.processDeclarationsForTemplateBody(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    processDeclarationsImpl(processor, state, lastParent, place)


  override def isInterface: Boolean = true

  override protected def baseIcon: Icon = Icons.TRAIT

  override def hasModifierProperty(name: String): Boolean = name match {
    case PsiModifier.ABSTRACT if isInterface => true
    case _ => super.hasModifierProperty(name)
  }

  override protected def isInterface(namedElement: PsiNamedElement): Boolean = true

  /** static forwarders for trait companion objects are only generated starting with scala 2.12 */
  override protected def addFromCompanion(companion: ScTypeDefinition): Boolean =
    this.scalaLanguageLevelOrDefault >= Scala_2_12

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }

  override def fakeCompanionClass: PsiClass = {
    new PsiClassWrapper(this, withSuffix(getQualifiedName), withSuffix(getName))
  }

  private def withSuffix(name: String) = s"$name$$class"

  override def constructor: Option[ScPrimaryConstructor] =
    this.stubOrPsiChild(ScalaElementType.PRIMARY_CONSTRUCTOR)

  override protected def keywordTokenType: IElementType = ScalaTokenType.TraitKeyword

  override def namedTag: Option[ScNamedElement] = Some(this)

  override protected def endParent: Option[PsiElement] = extendsBlock.templateBody
}
package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub

/**
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
class ScTraitImpl private (stub: StubElement[ScTemplateDefinition], nodeType: IElementType, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node) with ScTrait with ScTypeParametersOwner with ScTemplateDefinition {
  override def additionalJavaNames: Array[String] = {
    Array(fakeCompanionClass.getName) //do not add fakeCompanionModule => will build tree from stubs everywhere
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(null, null, node)}
  def this(stub: ScTemplateDefinitionStub) = {this(stub, ScalaElementTypes.TRAIT_DEF, null)}

  override def toString: String = "ScTrait: " + name

  override def getIconInner = Icons.TRAIT

  import com.intellij.psi._
  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place) &&
    super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  override def isInterface = true

  override def hasModifierProperty(name: String): Boolean = name match {
    case PsiModifier.ABSTRACT => true
    case _ => super.hasModifierProperty(name)
  }

  override protected def isInterfaceNode(node: SignatureNodes.Node) = true

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull

  override protected def syntheticMethodsNoOverrideImpl: Seq[PsiMethod] = {
    SyntheticMembersInjector.inject(this, withOverride = false)
  }
}
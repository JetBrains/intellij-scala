package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ScSubstitutor}

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
class ScTraitImpl private (stub: ScTemplateDefinitionStub, node: ASTNode)
  extends ScTypeDefinitionImpl(stub, ScalaElementTypes.TRAIT_DEFINITION, node) with ScTrait with ScTypeParametersOwner with ScTemplateDefinition {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateDefinitionStub) = this(stub, null)

  override def additionalJavaNames: Array[String] = {
    Array(fakeCompanionClass.getName) //do not add fakeCompanionModule => will build tree from stubs everywhere
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }


  override def toString: String = "ScTrait: " + ifReadAllowed(name)("")

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


  override def isInterface: Boolean = true

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

  override def hasModifierProperty(name: String): Boolean = name match {
    case PsiModifier.ABSTRACT if isInterface => true
    case _ => super.hasModifierProperty(name)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    res ++= getConstructors
    TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) { node =>
      this.processPsiMethodsForNode(node, isStatic = false, isInterface = true)(res += _)
    }

    for (synthetic <- syntheticMethodsNoOverride) {
      this.processPsiMethodsForNode(new SignatureNodes.Node(new PhysicalSignature(synthetic, ScSubstitutor.empty),
        ScSubstitutor.empty),
        isStatic = false, isInterface = isInterface)(res += _)
    }
    res.toArray
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }

  override protected def syntheticMethodsNoOverrideImpl: Seq[PsiMethod] = {
    SyntheticMembersInjector.inject(this, withOverride = false)
  }
}
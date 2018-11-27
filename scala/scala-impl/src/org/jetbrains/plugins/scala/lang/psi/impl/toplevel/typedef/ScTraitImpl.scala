package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiClass, PsiElementVisitor}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor

import scala.collection.mutable

/**
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
final class ScTraitImpl private[psi](stub: ScTemplateDefinitionStub[ScTrait],
                                     nodeType: ScTemplateDefinitionElementType[ScTrait],
                                     node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node)
    with ScTrait with ScTypeParametersOwner with ScTemplateDefinition {

  //do not add fakeCompanionModule => will build tree from stubs everywhere
  override def additionalJavaClass: Option[PsiClass] = Some(fakeCompanionClass)

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }


  override def toString: String = "ScTrait: " + ifReadAllowed(name)("")

  import com.intellij.psi._
  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {

    desugaredElement match {
      case Some(td) => return td.processDeclarationsForTemplateBody(processor, state, getLastChild, place)
      case _ =>
    }
    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place) &&
    super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }


  override def isInterface: Boolean = true

  override def psiMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

  override def hasModifierProperty(name: String): Boolean = name match {
    case PsiModifier.ABSTRACT if isInterface => true
    case _ => super.hasModifierProperty(name)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = mutable.ArrayBuffer.empty[PsiMethod]
    res ++= getConstructors
    TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) { node =>
      this.processPsiMethodsForNode(node, isStatic = false, isInterface = true)(res += _)
    }

    for (synthetic <- syntheticMethods) {
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
}
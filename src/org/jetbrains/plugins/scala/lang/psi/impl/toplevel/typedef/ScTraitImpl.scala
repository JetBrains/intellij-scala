package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions.{toPsiMemberExt, toPsiClassExt}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
class ScTraitImpl extends ScTypeDefinitionImpl with ScTrait with ScTypeParametersOwner with ScTemplateDefinition {
  override def additionalJavaNames: Array[String] = {
    Array(fakeCompanionClass.getName)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

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


  override def isInterface: Boolean = true

  def fakeCompanionClass: PsiClass = new PsiClassWrapper(this, getQualifiedName + "$class", getName + "$class")

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    res ++= getConstructors
    TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) { node =>
      this.processPsiMethodsForNode(node, isStatic = false, isInterface = true)(res += _)
    }
    res.toArray
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }
}
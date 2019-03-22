package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12

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

  override def additionalClassJavaName: Option[String] = Option(getName).map(withSuffix)

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

  override def hasModifierProperty(name: String): Boolean = name match {
    case PsiModifier.ABSTRACT if isInterface => true
    case _ => super.hasModifierProperty(name)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = mutable.ArrayBuffer.empty[PsiMethod]
    res ++= getConstructors

    TypeDefinitionMembers.getSignatures(this).allSignatures.foreach {
      this.processWrappersForSignature(_, isStatic = false, isInterface = true)(res += _)
    }

    if (this.scalaLanguageLevelOrDefault >= Scala_2_12) {
      /** static forwarders for trait companion objects are only generated starting with scala 2.12 */
      ScalaPsiUtil
        .getCompanionModule(this)
        .foreach(companion =>
          TypeDefinitionMembers.getSignatures(companion).allSignatures.foreach {
            this.processWrappersForSignature(_, isStatic = true, isInterface = false)(res += _)
          }
        )
    }

    res.toArray
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.orNull

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }

  def fakeCompanionClass: PsiClass = {
    new PsiClassWrapper(this, withSuffix(getQualifiedName), withSuffix(getName))
  }

  private def withSuffix(name: String) = s"$name$$class"

}
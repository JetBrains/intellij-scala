package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.light.PsiClassWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_12

/**
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
final class ScTraitImpl private[psi](stub: ScTemplateDefinitionStub[ScTrait],
                                     nodeType: ScTemplateDefinitionElementType[ScTrait],
                                     node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node, ScalaTokenTypes.kTRAIT)
    with ScTrait
    with ScTypeParametersOwner {

  override def additionalClassJavaName: Option[String] = Option(getName).map(withSuffix)

  override def toString: String = "ScTrait: " + ifReadAllowed(name)("")

  import com.intellij.psi._
  import com.intellij.psi.scope.PsiScopeProcessor

  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                                  state: ResolveState,
                                                  lastParent: PsiElement,
                                                  place: PsiElement): Boolean = desugaredElement match {
    case Some(td: ScTemplateDefinitionImpl[_]) =>
      td.processDeclarationsForTemplateBody(processor, state, getLastChild, place)
      case _ =>
        super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place) &&
          super.processDeclarationsForTemplateBody(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    processDeclarationsImpl(processor, state, lastParent, place)


  override def isInterface: Boolean = true

  //noinspection TypeAnnotation
  override protected def baseIcon = icons.Icons.TRAIT

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

  def fakeCompanionClass: PsiClass = {
    new PsiClassWrapper(this, withSuffix(getQualifiedName), withSuffix(getName))
  }

  private def withSuffix(name: String) = s"$name$$class"

}
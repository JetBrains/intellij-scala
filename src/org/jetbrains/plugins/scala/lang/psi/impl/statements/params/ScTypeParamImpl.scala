package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import javax.swing.Icon

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScTypeBoundsOwnerImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamStub
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScTypeUtil.AliasType

import scala.annotation.tailrec

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/

class ScTypeParamImpl private (stub: ScTypeParamStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.TYPE_PARAM, node)
    with ScTypeBoundsOwnerImpl with ScTypeParam with PsiClassFake {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTypeParamStub) = this(stub, null)

  @tailrec
  final override protected def extractBound(in: ScType, isLower: Boolean): ScType = {
    nameId.nextSibling match {
      case Some(pClause: ScTypeParamClause) =>
        val tParams = pClause.getChildren.toSeq
        in match {
          case ParameterizedType(des, params) =>
            if (params.length == tParams.length && params.forall(_.isInstanceOf[TypeParameterType]) &&
              params.map(_.asInstanceOf[TypeParameterType].psiTypeParameter) == tParams) {
              des
            } else {
              //here we should actually construct existential type for partial application
              in
            }
          case t => t.isAliasType match {
            case Some(AliasType(_: ScTypeAliasDefinition, Success(lower, _), _)) if isLower => extractBound(lower, isLower)
            case Some(AliasType(_: ScTypeAliasDefinition, _, Success(upper, _))) if !isLower => extractBound(upper, isLower)
            case None => t
          }
        }
      case _ => in
    }
  }

  override def toString: String = "TypeParameter: " + name

  def getOffsetInFile: Int = byPsiOrStub(getTextRange.getStartOffset)(_.positionInFile)

  def getContainingFileName: String = byStubOrPsi(_.containingFileName) {
    Option(getContainingFile).map(_.name).getOrElse("NoFile")
  }

  def getIndex: Int = 0
  def getOwner: PsiTypeParameterListOwner = getContext.getContext match {
    case c : PsiTypeParameterListOwner => c
    case _ => null
  }

  override def getContainingClass: ScTemplateDefinition = null

  def isCovariant: Boolean = byStubOrPsi(_.isCovariant) {
    Option(findChildByType[PsiElement](tIDENTIFIER))
      .exists(_.getText == "+")
  }

  def isContravariant: Boolean = byStubOrPsi(_.isContravariant) {
    Option(findChildByType[PsiElement](tIDENTIFIER))
      .exists(_.getText == "-")
  }

  def typeParameterText: String = byStubOrPsi(_.text)(getText)

  def owner: ScTypeParametersOwner = getContext.getContext.asInstanceOf[ScTypeParametersOwner]

  override def getUseScope: SearchScope = new LocalSearchScope(owner).intersectWith(super.getUseScope)

  def nameId: PsiElement = findLastChildByType(TokenSets.ID_SET)

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  override def viewTypeElement: Seq[ScTypeElement] =
    byPsiOrStub(super.viewTypeElement)(_.viewBoundsTypeElements)

  override def contextBoundTypeElement: Seq[ScTypeElement] =
    byPsiOrStub(super.contextBoundTypeElement)(_.contextBoundsTypeElements)

  override def lowerTypeElement: Option[ScTypeElement] =
    byPsiOrStub(super.lowerTypeElement)(_.lowerBoundTypeElement)

  override def upperTypeElement: Option[ScTypeElement] =
    byPsiOrStub(super.upperTypeElement)(_.upperBoundTypeElement)

  override def getIcon(flags: Int): Icon = {
    Icons.TYPE_ALIAS
  }

  override def getSuperTypes: Array[PsiClassType] = {
    // For Java
    upperBound match {
      case Success(t, _) =>
        val tp = t match {
          case ParameterizedType(des, _) if hasTypeParameters => des
          case _ => t
        }

        tp.toPsiType() match {
          case x: PsiClassType => Array(x)
          case _ => Array() // TODO
        }
      case Failure(_, _) => Array()
    }
  }

  override def isHigherKindedTypeParameter: Boolean =
    this.parent.filter(_.isInstanceOf[ScTypeParamClause]).flatMap(_.parent).exists(_.isInstanceOf[ScTypeParam])
}

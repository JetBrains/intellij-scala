package org.jetbrains.plugins.scala.lang.psi.impl.statements
package params

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.search.{LocalSearchScope, SearchScope}
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScContextBound, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScTypeBoundsOwnerImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTypeParamStub
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ScType}

import javax.swing.Icon
import scala.annotation.tailrec

class ScTypeParamImpl private (stub: ScTypeParamStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.TYPE_PARAM, node)
    with ScTypeBoundsOwnerImpl with ScTypeParam with PsiClassFake {

  def this(node: ASTNode) =  this(null, node)

  def this(stub: ScTypeParamStub) = this(stub, null)

  override lazy val typeParamId: Long = reusableId(this)

  @tailrec
  final override protected def extractBound(in: ScType, isLower: Boolean): ScType = typeParametersClause match {
    case Some(pClause: ScTypeParamClause) =>
      val tParams = pClause.typeParameters
      in match {
        case ParameterizedType(des, params)
          if params.length == tParams.length &&
            params.collect { case tpt: TypeParameterType => tpt.psiTypeParameter } == tParams => des
        case AliasType(_: ScTypeAliasDefinition, Right(lower), _) if isLower  => extractBound(lower, isLower)
        case AliasType(_: ScTypeAliasDefinition, _, Right(upper)) if !isLower => extractBound(upper, isLower)
        case t                                                                => ScTypePolymorphicType(t, tParams.map(TypeParameter(_)))
      }
    case _ => in
  }

  override def toString: String = s"TypeParameter: ${ifReadAllowed(name)("")}"

  override def getContainingFileName: String = byStubOrPsi(_.containingFileName) {
    Option(getContainingFile).map(_.name).getOrElse("NoFile")
  }

  override def getIndex: Int = 0

  override def getOwner: PsiTypeParameterListOwner = getContext.getContext match {
    case c: PsiTypeParameterListOwner => c
    case _                            => null
  }

  override def getContainingClass: ScTemplateDefinition = null

  override def isCovariant: Boolean = _isCovariant()

  private val _isCovariant = cached("isCovariant", ModTracker.anyScalaPsiChange, () => {
    byStubOrPsi(_.isCovariant) {
      Option(findChildByType[PsiElement](tIDENTIFIER))
        .exists(_.textMatches("+"))
    }
  })

  override def isContravariant: Boolean = _isContravariant()

  private val _isContravariant = cached("isContravariant", ModTracker.anyScalaPsiChange, () => {
    byStubOrPsi(_.isContravariant) {
      Option(findChildByType[PsiElement](tIDENTIFIER))
        .exists(_.textMatches("-"))
    }
  })

  override def typeParameterText: String = byStubOrPsi(_.text)(getText)

  override def owner: ScTypeParametersOwner = {
    val result = getContext.getContext
    // To see more info about EA-239302
    try {
      result.asInstanceOf[ScTypeParametersOwner]
    } catch {
      case exception: ClassCastException =>
        val errorDescription = result.asOptionOf[PsiErrorElement].map(_.getErrorDescription).getOrElse("")
        throw new IllegalStateException(s"Error: $errorDescription", exception)
    }
  }

  override def getUseScope: SearchScope = {
    val typeParamOwner = owner

    val superSearchScope = super.getUseScope
    val ownerSearchScope = new LocalSearchScope(typeParamOwner)


    val result0 = ownerSearchScope.intersectWith(superSearchScope)

    /**
     * In case of scala 3 enum case the scaladoc is attached to ScEnumCases, not ScEnumCase
     * (see comment to [[org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases]])
     * so we need to add it's scope as well.
     * In all other cases the document is attached to the owner and is included into ownerSearchScope
     */
    val extraScaladocScope = typeParamOwner match {
      case enumCase: ScEnumCase =>
        enumCase.getParent.asInstanceOf[ScEnumCases].docComment.map(new LocalSearchScope(_))
      case _ =>
        None
    }

    extraScaladocScope match {
      case Some(scope) =>
        result0.union(scope)
      case None =>
        result0
    }
  }

  override def nameId: PsiElement = findLastChildByType(TokenSets.ID_SET).orNull

  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  override def viewTypeElement: Seq[ScTypeElement] =
    byPsiOrStub(super.viewTypeElement)(_.viewBounds)

  override def contextBounds: Seq[ScContextBound] =
    byPsiOrStub(super.contextBounds)(_.contextBounds)

  override def lowerTypeElement: Option[ScTypeElement] =
    byPsiOrStub(super.lowerTypeElement)(_.lowerBoundTypeElement)

  override def upperTypeElement: Option[ScTypeElement] =
    byPsiOrStub(super.upperTypeElement)(_.upperBoundTypeElement)

  override def getIcon(flags: Int): Icon = {
    Icons.TYPE_ALIAS
  }

  override def getSuperTypes: Array[PsiClassType] =
  // For Java
    upperBound.toOption.map {
      case ParameterizedType(des, _) if hasTypeParameters => des
      case t => t
    }.flatMap {
      _.toPsiType match {
        case x: PsiClassType => Some(x)
        case _ => None // TODO
      }
    }.toArray

  override def isHigherKindedTypeParameter: Boolean =
    this.parent.filter(_.is[ScTypeParamClause]).flatMap(_.parent).exists(_.is[ScTypeParam])

}

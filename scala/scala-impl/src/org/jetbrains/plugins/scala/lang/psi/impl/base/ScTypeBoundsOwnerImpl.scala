package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeBoundsOwner, ScTypeParametersOwner}
import org.jetbrains.plugins.scala.lang.psi.types.api.{ParameterizedType, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, ScType, api}
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

import scala.annotation.tailrec

trait ScTypeBoundsOwnerImpl extends ScTypeBoundsOwner with ScTypeParametersOwner {

  override def lowerBound: TypeResult = typeOf(lowerTypeElement, isLower = true)

  override def upperBound: TypeResult = typeOf(upperTypeElement, isLower = false)

  @tailrec
  private final def extractBound(in: ScType, isLower: Boolean): ScType = typeParametersClause match {
    case Some(pClause: ScTypeParamClause) =>
      val tParams = pClause.typeParameters
      in match {
        case AliasType(_: ScTypeAliasDefinition, Right(lower), _) if isLower  => extractBound(lower, isLower)
        case AliasType(_: ScTypeAliasDefinition, _, Right(upper)) if !isLower => extractBound(upper, isLower)
        case t                                                                => ScTypePolymorphicType(t, tParams.map(TypeParameter(_)))
      }
    case _ => in
  }

  override def upperTypeElement: Option[ScTypeElement] =
    findLastChildByTypeScala[PsiElement](ScalaTokenTypes.tUPPER_BOUND)
      .flatMap(_.nextSiblingOfType[ScTypeElement])

  override def lowerTypeElement: Option[ScTypeElement] =
    findLastChildByTypeScala[PsiElement](ScalaTokenTypes.tLOWER_BOUND)
      .flatMap(_.nextSiblingOfType[ScTypeElement])

  private def typeOf(typeElement: Option[ScTypeElement], isLower: Boolean): TypeResult =
    typeElement match {
      case Some(elem) =>
        if (ScalaApplicationSettings.PRECISE_TEXT) elem.`type`() // SCL-21151
        else elem.`type`().map(extractBound(_, isLower))
      case None => Right(if (isLower) api.Nothing else api.Any)
    }
}
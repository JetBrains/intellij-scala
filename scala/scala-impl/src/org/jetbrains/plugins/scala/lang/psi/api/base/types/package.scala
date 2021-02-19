package org.jetbrains.plugins.scala.lang.psi.api.base

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author adkozlov
  */
package object types {
  type ScAnnotTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScAnnotTypeElement

  type ScCompoundTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScCompoundTypeElement
  val ScCompoundTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScCompoundTypeElement

  type ScDependentFunctionTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScDependentFunctionTypeElement
  val ScDependentFunctionTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScDependentFunctionTypeElement

  type ScExistentialClause = org.jetbrains.plugins.scala.lang.psi.api.ScExistentialClause

  type ScExistentialTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScExistentialTypeElement

  type ScFunctionalTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScFunctionalTypeElement
  val ScFunctionalTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScFunctionalTypeElement

  type ScInfixLikeTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScInfixLikeTypeElement

  type ScInfixTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScInfixTypeElement
  val ScInfixTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScInfixTypeElement

  type ScLiteralTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScLiteralTypeElement

  type ScMatchTypeCase = org.jetbrains.plugins.scala.lang.psi.api.ScMatchTypeCase
  val ScMatchTypeCase = org.jetbrains.plugins.scala.lang.psi.api.ScMatchTypeCase

  type ScMatchTypeCases = org.jetbrains.plugins.scala.lang.psi.api.ScMatchTypeCases
  val ScMatchTypeCases = org.jetbrains.plugins.scala.lang.psi.api.ScMatchTypeCases

  type ScMatchTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScMatchTypeElement
  val ScMatchTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScMatchTypeElement

  type ScParameterizedTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScParameterizedTypeElement
  val ScParameterizedTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScParameterizedTypeElement

  type ScParenthesisedTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScParenthesisedTypeElement
  val ScParenthesisedTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScParenthesisedTypeElement

  type ScPolyFunctionTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScPolyFunctionTypeElement

  type ScQuotedType = org.jetbrains.plugins.scala.lang.psi.api.ScQuotedType

  type ScRefineStat = org.jetbrains.plugins.scala.lang.psi.api.ScRefineStat

  type ScRefinement = org.jetbrains.plugins.scala.lang.psi.api.ScRefinement

  type ScSelfTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScSelfTypeElement

  type ScSequenceArg = org.jetbrains.plugins.scala.lang.psi.api.ScSequenceArg

  type ScSimpleTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScSimpleTypeElement
  val ScSimpleTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScSimpleTypeElement

  type ScStableId = org.jetbrains.plugins.scala.lang.psi.api.ScStableId

  type ScTupleTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScTupleTypeElement
  val ScTupleTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScTupleTypeElement

  type ScTypeArgs = org.jetbrains.plugins.scala.lang.psi.api.ScTypeArgs

  type ScTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScTypeElement
  val ScTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScTypeElement

  type ScTypeLambdaTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScTypeLambdaTypeElement
  val ScTypeLambdaTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScTypeLambdaTypeElement

  type ScTypeProjection = org.jetbrains.plugins.scala.lang.psi.api.ScTypeProjection

  type ScTypeVariableTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScTypeVariableTypeElement

  type ScTypes = org.jetbrains.plugins.scala.lang.psi.api.ScTypes

  type ScWildcardTypeElement = org.jetbrains.plugins.scala.lang.psi.api.ScWildcardTypeElement

  implicit class ScTypeElementExt(private val typeElement: ScTypeElement) extends AnyVal {
    def calcType: ScType = typeElement.`type`().getOrAny

    def getParamTypeText: String =
      if (typeElement.isRepeated) s"_root_.scala.Seq[${typeElement.getText}]"
      else                        typeElement.getText
  }
}
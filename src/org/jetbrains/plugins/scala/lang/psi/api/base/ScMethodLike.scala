package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * A member that can be converted to a ScMethodType, ie a method or a constructor.
 */
trait ScMethodLike extends ScMember with PsiMethod {
  def methodType: ScType = methodType(None)
  def methodType(result: Option[ScType]): ScType

  /**
   * This method is very important for generic type inference.
   * In case if we use just containg class type parameters
   * we can get problems about intersection of just class
   * type parameters and constructor type parameters. And
   * in that context it will have different meaning. See SCL-3095.
   * @return generated type parameters only for constructors
   */
  def getConstructorTypeParameters: Option[ScTypeParamClause] = {
    CachesUtil.get(this, CachesUtil.CONSTRUCTOR_TYPE_PARAMETERS_KEY,
      new CachesUtil.MyProvider[ScMethodLike, Option[ScTypeParamClause]](
        this, (value: ScMethodLike) => value.getConstructorTypeParametersImpl
      )(PsiModificationTracker.MODIFICATION_COUNT)
    )
  }

  /** If this is a primary or auxilliary constructor, return the containing classes type parameter clause */
  def getClassTypeParameters: Option[ScTypeParamClause] = {
    if (isConstructor) {
      containingClass match {
        case c: ScTypeDefinition => c.typeParametersClause
        case _ => None
      }
    } else None
  }

  def effectiveParameterClauses: Seq[ScParameterClause]

  def parameterList: ScParameters

  def addParameter(param: ScParameter): ScMethodLike = {
    if (parameterList.clauses.length > 0)
      parameterList.clauses.apply(0).addParameter(param)
    else {
      val clause: ScParameterClause = ScalaPsiElementFactory.createClauseFromText("()", getManager)
      val newClause = clause.addParameter(param)
      parameterList.addClause(newClause)
    }
    this
  }

  private def getConstructorTypeParametersImpl: Option[ScTypeParamClause] = {
    this match {
      case method: PsiMethod if method.isConstructor =>
        val clazz = method.containingClass
        clazz match {
          case c: ScTypeDefinition =>
            c.typeParametersClause.map((typeParamClause: ScTypeParamClause) => {
              val paramClauseText = typeParamClause.getTextByStub
              ScalaPsiElementFactory.createTypeParameterClauseFromTextWithContext(paramClauseText,
                typeParamClause.getContext, typeParamClause)
            })
          case _ => None
        }
      case _ => None
    }
  }

  def isExtensionMethod: Boolean = false
}
package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiTypeParametersOwnerAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createClauseFromText, createTypeParameterClauseFromTextWithContext}
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInsidePsiElement, ModCount}

/**
 * A member that can be converted to a ScMethodType, ie a method or a constructor.
 */
trait ScMethodLike extends ScMember with PsiMethod with PsiTypeParametersOwnerAdapter {

  /**
   * This method is very important for generic type inference.
   * In case if we use just containg class type parameters
   * we can get problems about intersection of just class
   * type parameters and constructor type parameters. And
   * in that context it will have different meaning. See SCL-3095.
   * @return generated type parameters only for constructors
   */
  @CachedInsidePsiElement(this, ModCount.getBlockModificationCount)
  def getConstructorTypeParameters: Option[ScTypeParamClause] = {
    ScMethodLike.this match {
      case method: PsiMethod if method.isConstructor =>
        val clazz = method.containingClass
        clazz match {
          case c: ScTypeDefinition =>
            c.typeParametersClause.map((typeParamClause: ScTypeParamClause) => {
              val paramClauseText = typeParamClause.getTextByStub
              createTypeParameterClauseFromTextWithContext(paramClauseText, typeParamClause.getContext, typeParamClause)
            })
          case _ => None
        }
      case _ => None
    }
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
      val clause = createClauseFromText("()")
      val newClause = clause.addParameter(param)
      parameterList.addClause(newClause)
    }
    this
  }

  def isExtensionMethod: Boolean = false
}
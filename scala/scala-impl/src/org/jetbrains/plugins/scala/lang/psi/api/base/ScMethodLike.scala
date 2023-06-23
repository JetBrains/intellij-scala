package org.jetbrains.plugins.scala.lang.psi.api.base

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiTypeParametersOwnerAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createTypeParameterClauseFromTextWithContext

/**
 * A member, that can have ScMethodType, i.e. a method or a constructor.
 */
trait ScMethodLike
  extends ScMember
    with PsiMethod
    with PsiTypeParametersOwnerAdapter
    with ScParameterOwner.WithContextBounds {

  def getConstructorTypeParameterClause: Option[ScTypeParamClause] = cachedInUserData("getConstructorTypeParameterClause", this, BlockModificationTracker(this)) {
    ScMethodLike.this match {
      case constructor @ ScalaConstructor.in(c: ScTypeDefinition) =>
        c.typeParametersClause.map { clause =>
          val paramClauseText = clause.getTextByStub
          createTypeParameterClauseFromTextWithContext(paramClauseText, constructor, constructor.parameterList)
        }
      case _ => None
    }
  }

  def getConstructorTypeParameters: Seq[ScTypeParam] =
    getConstructorTypeParameterClause.fold(Seq.empty[ScTypeParam])(_.typeParameters)

  /** If this is a primary or auxilliary constructor, return the containing classes type parameter clause */
  def getClassTypeParameters: Option[ScTypeParamClause] = {
    if (isConstructor) {
      containingClass match {
        case c: ScTypeDefinition => c.typeParametersClause
        case _                   => None
      }
    } else None
  }

  def parameterList: ScParameters

  final def parametersInClause(clauseIndex: Int): Seq[ScParameter] =
    effectiveParameterClauses match {
      case clauses if clauses.indices.contains(clauseIndex) =>
        clauses(clauseIndex).effectiveParameters
      case _ => Seq.empty
    }
}
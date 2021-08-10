package org.jetbrains.plugins.scala
package lang
package psi
package api
package base

import com.intellij.psi.PsiMethod
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiTypeParametersOwnerAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScParameterOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

/**
 * A member, that can have ScMethodType, i.e. a method or a constructor.
 */
trait ScMethodLike
  extends ScMember
    with PsiMethod
    with PsiTypeParametersOwnerAdapter
    with ScParameterOwner.WithContextBounds {

  /** If this is a constructor, return containing class' type parameters clause */
  @CachedInUserData(this, BlockModificationTracker(this))
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
package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition

import scala.collection.immutable.SeqMap

private class ContextDependent[A] private (stateToValue: Map[SeqMap[ScTypeAliasDefinition, Boolean], A]) {
  def this() = this(Map.empty)

  def get(implicit context: Context): Option[A] = stateToValue.collectFirst {
    case (state, value) if state.forall { case (opaqueTypeAlias, isInScope) => context.isInScopeOf(opaqueTypeAlias) == isInScope} => value
  }

  def updatedUsing(f: Context => A)(implicit context: Context): (A, ContextDependent[A]) = {
    var state = SeqMap.empty[ScTypeAliasDefinition, Boolean]

    val value = f(opaqueTypeAlias => {
      val isInScope = context.isInScopeOf(opaqueTypeAlias)
      state += opaqueTypeAlias -> isInScope
      isInScope
    })

    (value, new ContextDependent[A](stateToValue.updated(state, value)))
  }
}

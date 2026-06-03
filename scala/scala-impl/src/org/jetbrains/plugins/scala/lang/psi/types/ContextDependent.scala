package org.jetbrains.plugins.scala.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAliasDefinition

import scala.collection.immutable.SeqMap

private class ContextDependent[A] private (stateToValue: Map[SeqMap[ScTypeAliasDefinition, Boolean], A]) { // Use a trie?
  def this() = this(Map.empty)

  def get(implicit context: Context): Option[A] = stateToValue.collectFirst {
    case (state, value) if state.forall { case (opaqueTypeAlias, isInScope) => context.isInScopeOf(opaqueTypeAlias) == isInScope} => value
  }

  def updatedUsing(f: Context => A)(implicit context: Context): (A, ContextDependent[A]) = {
    var state = SeqMap.empty[ScTypeAliasDefinition, Boolean]

    val value = f(new Context {
      override def isInScopeOf(opaqueTypeAlias: ScTypeAliasDefinition): Boolean = {
        val isInScope = context.isInScopeOf(opaqueTypeAlias)
        state += opaqueTypeAlias -> isInScope
        isInScope
      }

      override def toString: String = context.toString
    })

    (value, new ContextDependent[A](stateToValue.updated(state, value)))
  }

  override def toString: String = if (stateToValue.isEmpty) "<empty>" else {
    val mappings = stateToValue.map { case (state, value) =>
      val aliases = state.map { case (opaqueTypeAlias, isInScope) => s"${opaqueTypeAlias.name}: $isInScope" }
      "(" + aliases.mkString(", ") + ") -> " + value
    }
    mappings.mkString(", ")
  }
}

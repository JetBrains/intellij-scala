package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.{LeafType, ScType}

private abstract class LeafSubstitution extends SimpleUpdate {

  protected val subst: PartialFunction[LeafType, ScType]

  override def apply(scType: ScType): AfterUpdate = scType match {
    //we shouldn't go deeper even if this substitution can't process `scType`
    //to allow application of several of them in the same pass
    case leaf: LeafType => ReplaceWith(subst.applyOrElse(leaf, identity[ScType]))

    case _ => ProcessSubtypes
  }
}

object LeafSubstitution {
  def apply(pf: PartialFunction[LeafType, ScType]): SimpleUpdate = new LeafSubstitution {
    override protected val subst: PartialFunction[LeafType, ScType] = pf
  }
}

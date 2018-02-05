package org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate

import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.AfterUpdate.{ProcessSubtypes, ReplaceWith}
import org.jetbrains.plugins.scala.lang.psi.types.{ScAbstractType, ScType}

/**
  * Nikolay.Tropin
  * 01-Feb-18
  */
private trait Substitution extends Update {

  protected val subst: PartialFunction[ScType, ScType]

  def apply(scType: ScType): AfterUpdate = scType match {
    case _: ScAbstractType |
         _: UndefinedType |
         _: TypeParameterType |         //we shouldn't go deeper even if this substitution can't process `scType`
         _: ScThisType |                //to allow application of several of them in the same pass
         _: ScDesignatorType          => ReplaceWith(subst.applyOrElse(scType, identity[ScType]))

    case _                            => ProcessSubtypes
  }

  def toString: String
}

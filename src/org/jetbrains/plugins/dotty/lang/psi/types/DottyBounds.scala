package org.jetbrains.plugins.dotty.lang.psi.types

import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}

/**
  * @author adkozlov
  */
trait DottyBounds extends api.Bounds {
  typeSystem: api.TypeSystem =>

  override def glb(first: ScType, second: ScType, checkWeak: Boolean): ScType = {
    checkTypes(first, second) match {
      case DottyNoType() =>
      case result => return result
    }

    second match {
      case DottyOrType(left, right) =>
        def lift: ScType => ScType = glb(first, _, checkWeak)
        return lub(lift(left), lift(right), checkWeak)
      case _ =>
    }

    first match {
      case DottyOrType(left, right) =>
        def lift: ScType => ScType = glb(_, second, checkWeak)
        return lub(lift(left), lift(right), checkWeak)
      case _ =>
    }

    mergeIfSub(first, second, checkWeak) match {
      case DottyNoType() =>
      case result => return result
    }

    mergeIfSub(second, first, checkWeak) match {
      case DottyNoType() =>
      case result => return result
    }

    if (first.isInstanceOf[DottyConstantType] && second.isInstanceOf[DottyConstantType]) Nothing
    else andType(first, second)
  }

  override def glb(types: Seq[ScType], checkWeak: Boolean): ScType = {
    types.foldLeft(Any: ScType)(glb(_, _, checkWeak))
  }

  override def lub(first: ScType, second: ScType, checkWeak: Boolean): ScType = {
    checkTypes(first, second) match {
      case DottyNoType() =>
      case result => return result
    }

    mergeIfSuper(first, second, checkWeak) match {
      case DottyNoType() =>
      case result => return result
    }

    mergeIfSuper(second, first, checkWeak) match {
      case DottyNoType() =>
      case result => return result
    }

    // TODO: widen types

    orType(first, second)
  }

  override def lub(types: Seq[ScType], checkWeak: Boolean): ScType = {
    types.foldLeft(Nothing: ScType)(lub(_, _, checkWeak))
  }

  private def checkTypes(first: ScType, second: ScType) = {
    if (first.eq(second)) first
    else first match {
      case DottyNoType() => second
      case _ => second match {
        case DottyNoType() => first
        case _ => DottyNoType() // TODO: is refined of Any/Nothing
      }
    }
  }

  // TODO: frozen?
  private def mergeIfSuper(first: ScType, second: ScType, checkWeak: Boolean): ScType = {
    second match {
      case DottyOrType(left, right) => merge(first, second, left, right, checkWeak, mergeIfSuper, lub)
      case _ => DottyNoType()
    }
  }

  private def orType(first: ScType, second: ScType): ScType = {
    distributedOr(first, second) match {
      case DottyNoType() =>
      case result => return result
    }

    distributedOr(second, first) match {
      case DottyNoType() =>
      case result => return result
    }

    // TODO erasure, liftHk
    DottyOrType(first, second)
  }

  private def distributedOr(first: ScType, second: ScType) = DottyNoType()

  // TODO: frozen?
  private def mergeIfSub(first: ScType, second: ScType, checkWeak: Boolean): ScType = {
    second match {
      case DottyAndType(left, right) => merge(first, second, left, right, checkWeak, mergeIfSub, glb)
      case _ => DottyNoType()
    }
  }

  private def merge(first: ScType, second: ScType, left: ScType, right: ScType,
                    checkWeak: Boolean,
                    merger: (ScType, ScType, Boolean) => ScType,
                    getBound: (ScType, ScType, Boolean) => ScType) = {
    merger(first, left, checkWeak) match {
      case bound if bound.eq(left) => second
      case DottyNoType() => merger(first, right, checkWeak) match {
        case bound if bound.eq(right) => second
        case DottyNoType() => DottyNoType()
        case bound => getBound(left, bound, checkWeak)
      }
      case bound => getBound(bound, right, checkWeak)
    }
  }

  private def andType(first: ScType, second: ScType): ScType = {
    distributedAnd(first, second) match {
      case DottyNoType() =>
      case result => return result
    }

    distributedAnd(second, first) match {
      case DottyNoType() =>
      case result => return result
    }

    // TODO erasure, liftHk
    DottyAndType(first, second)
  }

  private def distributedAnd(second: ScType, first: ScType) = DottyNoType()
}

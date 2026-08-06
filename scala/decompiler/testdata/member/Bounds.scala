package member

trait Bounds {
  type AbstractTypeLowerBound >: Int

  type AbstractTypeUpperBound <: Int

  type AbstractTypeLowerAndUpperBounds >: Int <: AnyVal
}
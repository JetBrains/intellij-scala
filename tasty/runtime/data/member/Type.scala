package member

trait Type {
  type Abstract

  type Alias = Int

  type LowerBound >: Int

  type UpperBound <: Int

  type LowerAndUpperBounds >: Int
}
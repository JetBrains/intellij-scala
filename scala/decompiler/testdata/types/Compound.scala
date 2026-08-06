package types

trait Compound {
  type T1 = Int with Long

  type T2 = Int with Long with Float
}
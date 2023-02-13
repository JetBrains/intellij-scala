package types

trait Or {
  type T1 = Int | Long

  type T2 = (Int | Long) | Float
}
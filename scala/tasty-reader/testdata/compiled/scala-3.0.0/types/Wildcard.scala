package types

trait Wildcard {
  class TC[A]

  type T1 = TC[?]

  type T2 = TC[? >: Int]

  type T3 = TC[? <: Int]

  type T4 = TC[? >: Int <: AnyVal]
}
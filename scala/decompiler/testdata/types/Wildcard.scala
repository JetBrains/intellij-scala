package types

trait Wildcard {
  class TC[A]

  type T1 = TC[_]

  type T2 = TC[_ >: Int]

  type T3 = TC[_ <: Int]

  type T4 = TC[_ >: Int <: AnyVal]
}
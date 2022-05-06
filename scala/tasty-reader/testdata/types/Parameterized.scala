package types

trait Parameterized {
  class C1[A]

  class C2[A, B]

  type T1 = C1[Int]

  type T2 = C2[Int, Long]
}
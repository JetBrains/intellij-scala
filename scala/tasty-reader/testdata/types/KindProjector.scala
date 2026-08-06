package types

trait KindProjector {
  class HKT1[A[_]]

  class HKT2[A[_, _]]

  class TC1[A]

  class TC2[A, B]

  class TC3[A, B, C]

  type T0 = HKT1[TC1[*]]

  type T1 = HKT1[TC2[*, Int]]

  type T2 = HKT1[TC2[Int, *]]

  type T3 = HKT1[TC3[*, Int, Long]]

  type T4 = HKT2[TC3[*, *, Int]]
}
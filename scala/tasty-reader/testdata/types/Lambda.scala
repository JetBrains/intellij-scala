package types

trait Lambda {
  class HKT1[A[_]]

  class HKT1a[A[_ >: Int <: AnyVal]]

  class HKT2[A[_, _]]

  class TC1[A]

  class TC2[A, B]

  type T1 = HKT1[TC1]

  type T2 = HKT2[TC2]

  type T3 = HKT1[[X] =>> TC2[X, Int]]

  type T4 = HKT1a[[X >: Int <: AnyVal] =>> TC2[X, Int]]

  type T5 = HKT2[[X, Y] =>> TC2[X, Y]]
}
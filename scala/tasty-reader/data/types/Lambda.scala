package types

trait Lambda {
  class HKT1[A[_]]

  class HKT2[A[_ >: Int <: AnyVal]]

  class HKT3[A[_, _]]

  class TC[A, B]

  type T1 = HKT1[[X] =>> TC[X, Int]]

  type T2 = HKT2[[X >: Int <: AnyVal] =>> TC[X, Int]]

  type T3 = HKT3[[X, Y] =>> TC[X, Y]]
}
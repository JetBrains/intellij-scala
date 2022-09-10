package types

trait KindProjector {
  class HKT1[A[_]]

  class HKT2[A[_, _]]

  class TC2[A, B]

  class TC3[A, B, C]

  type T1 = HKT1[/**/TC2[*, Int]/*[_$4] =>> TC2[_$4, Int]*/]

  type T2 = HKT2[/**/TC3[*, *, Int]/*[_$5, _$6] =>> TC3[_$5, _$6, Int]*/]
}
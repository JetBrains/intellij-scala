package parameter

trait HKT {
  trait T1[A[_]]

  trait T2[A[_], B[_]]

  trait T3[A[_, _]]

  trait T4[A[_[_]]]

  trait T5[A[X]]

  trait T6[A[X], B[Y]]

  trait T7[A[X, Y]]

  trait T8[A[X[Z]]]

  type t0[_]

  type t1[_[_]]

  type t2[_[_], _[_]]

  type t3[_[_, _]]

  type t4[_[_[_]]]

  type t5[A[X]]

  type t6[A[X], B[Y]]

  type t7[A[X, Y]]

  type t8[A[X[Z]]]
}
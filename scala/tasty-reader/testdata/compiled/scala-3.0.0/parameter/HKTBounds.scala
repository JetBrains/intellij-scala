package parameter

trait HKTBounds {
  class TC1[A]

  class TC2[A, B]

  trait TraitLower[A[X >: Int] >: TC1[Int]]

  trait TraitUpper[A[X <: Int] <: TC1[Int]]

  trait TraitMultiple1[A[X >: Int] >: TC1[Int], B[X >: Int] <: TC1[Int]]

  trait TraitMultiple2[A[X >: Int, Y <: Int] >: TC2[Int, Int]]

  type TypeLower[A[X >: Int] >: TC1[Int]]

  type TypeUpper[A[X <: Int] <: TC1[Int]]

  type TypeMultiple1[A[X >: Int] >: TC1[Int], B[X >: Int] <: TC1[Int]]

  type TypeMultiple2[A[X >: Int, Y <: Int] >: TC2[Int, Int]]
}
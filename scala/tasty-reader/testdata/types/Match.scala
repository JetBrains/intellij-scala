package types

trait Match {
  type T1 = Int match { case Int => String }

  type T2 = Int match { case Int => String case String => Int }

  type T3[A] = A match { case Option[Int] => Seq[Int] }

  type T4[A] = A match { case Option[x] => Seq[x] }

  type T5[A] = A match { case Option[_] => Seq[Int] }

  type T6 <: String = Int match { case Int => String }

  type T7[A] <: String = Int match { case Int => String }
}
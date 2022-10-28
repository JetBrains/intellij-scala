package types

trait Refinement {
  type T1 = { def member: Int }

  type T2 = { def member1: Int; def member2: Int }

  type T3 = { val value: Int; def method(x: Int): Unit; type Type }

  type T4 = Int { def member: Int }

  type T5 = scala.collection.immutable.Seq[Int] { def member: Int }
}
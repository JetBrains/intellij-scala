package types

trait Annotated {
  type T1 = Int @unchecked

  type T2 = Seq[Int @unchecked]

  type T3 = Int @deprecated("text")

  type T4 = Int @scala.annotation.nowarn
}
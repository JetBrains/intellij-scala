case class X(x: Any)

class Sub2[XX <: X] {
  val value: XX = error("")
  import value._
  /* line: 1 */ x
}

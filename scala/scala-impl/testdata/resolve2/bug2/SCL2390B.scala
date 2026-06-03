case class X(x: Any)

trait PimpedType[T] {
  val value: T
}

class Sub extends PimpedType[X] {
  import value._
  /* line: 1 */ x // resolve error
}

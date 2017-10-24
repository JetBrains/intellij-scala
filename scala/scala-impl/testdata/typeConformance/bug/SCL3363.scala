object A {
  def foo[T[_]] { }
}
val a : { def foo[T] } = A
//False
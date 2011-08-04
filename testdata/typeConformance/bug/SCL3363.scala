object A {
  def foo[T[_]] { }
}
def a : { def foo[T] } = A
//False
trait A {
  def x: this.type
  def y: A
}

trait Foo[+A[T] <: B] {
  def newA[T]: A[T]

  val a: (A[Unit], A[Unit]) = /*start*/(newA.init, newA[Unit].init)/*end*/
}
trait B {
  def init: this.type = this
}
//(A[Unit], A[Unit])
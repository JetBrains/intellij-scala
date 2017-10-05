case class A[X >: scala.Nothing <: scala.Any](val x: X)
trait B
val a: Any =  A(new B{}).x
// True
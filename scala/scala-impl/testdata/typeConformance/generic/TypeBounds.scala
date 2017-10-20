trait A[X >: scala.Nothing <: scala.Any]
trait B
val a: A[_] = new A[B]{}
// True
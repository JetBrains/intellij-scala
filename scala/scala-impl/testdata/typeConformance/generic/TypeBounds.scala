trait A[X >: scala.Nothing <: scala.Any]
trait B
object Wrapper {
  val a: A[_] = new A[B]{}
}
// True
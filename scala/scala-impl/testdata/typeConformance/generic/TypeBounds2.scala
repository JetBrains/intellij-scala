case class A[X >: scala.Nothing <: scala.Any](val x: X)
trait B
object Wrapper {
  val a: Any =  A(new B{}).x
}
// True
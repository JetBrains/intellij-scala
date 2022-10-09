trait T1
trait T2 extends T1
trait T3 extends T2
trait A[X >: T3 <: T1]
object Wrapper {
  val a: A[_] = new A[T2]{}
}
// True
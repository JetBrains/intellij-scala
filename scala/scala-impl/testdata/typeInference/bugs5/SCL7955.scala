class A[-T]
class B
trait C
trait D
trait E extends C with D
implicit val c: C = new C {}
implicit def d[T <: D]: T = sys.exit()
implicit def a[T](a: A[T])(implicit t: T): B = new B
val b: B = /*start*/new A[E]/*end*/
def foo(implicit z: A[E] => B) = 123
foo

//B
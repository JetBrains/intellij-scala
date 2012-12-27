import java.util

class C[T]
val l: util.List[_ <: C[_]] = null
object Z {
  def foo(x: util.Collection[_ <: C[_]]): Int = 1
  def foo(x : Int) = false
}

/*start*/Z.foo(l)/*end*/
//Int
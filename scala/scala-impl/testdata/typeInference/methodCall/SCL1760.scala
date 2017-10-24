import collection.mutable.HashSet

class A
class B extends A

val set: HashSet[B] = new HashSet[B]
object C {
  def foo[T](x: Array[T], y: Int): Int = 345
  def foo(arr: Array[A]): Int = 346
}

/*start*/C.foo(set.toArray)/*end*/
//Int
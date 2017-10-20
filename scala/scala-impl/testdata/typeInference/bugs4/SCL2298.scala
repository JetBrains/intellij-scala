class A
class B extends A
val z = if (true) Array(new A) else Array(new B)
/*start*/z/*end*/
//Array[_ >: B <: A]
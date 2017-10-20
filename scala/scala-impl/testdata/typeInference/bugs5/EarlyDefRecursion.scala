import java.util

object EarlyDefRecursion {
  class A[T](t: T) {
    def foo = 1
  }

  class B extends {
    val x = new util.ArrayList[String]()
  } with A(x.get(1)) {
    /*start*/super.foo/*end*/
  }
}
//Int
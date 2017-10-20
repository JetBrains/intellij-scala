import java.lang.annotation.ElementType

object SCL8359 {
  class A[T]
  object A {
    implicit class B[T](a: A[T]) {
      def foo = 1
    }
  }
  val e: A[ElementType] = new A

  /*start*/e.foo/*end*/
}
//Int
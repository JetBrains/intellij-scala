object SCL3216 {
  trait T[X]
  class Foo extends T[Foo]
  object Foo extends Foo
  object Implicits {
    implicit def foo2x[X <: T[X]](f: T[X]): String = ""
  }


  import Implicits._
  /*start*/Foo.substring(0)/*end*/
}
//String
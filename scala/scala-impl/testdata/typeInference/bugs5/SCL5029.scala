object SCL5029 {

  object Tag {
    type Tagged[U] = {type Tag = U}
    type @@[T, U] = T with Tagged[U] with Object

    @inline def apply[A, T](a: A): A @@ T = a.asInstanceOf[A @@ T]
  }

  class Foo

  object Test {

    import Tag._

    sealed trait _MyId

    type MyId = Long @@ _MyId

    def foo(x: MyId): Int = 1
    def foo(s: String): String = s

    /*start*/foo(Tag[Long, _MyId](1))/*end*/
  }

}
//Int
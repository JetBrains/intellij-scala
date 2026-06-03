class SCL6116 {
  trait Foo {
    type T

    def apply(x: String): T
  }

  def useFoo(f: Foo): f.T = /*start*/f.apply("hello")/*end*/
}
//f.T
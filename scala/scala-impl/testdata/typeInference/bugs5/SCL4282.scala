object SCL4282 {
  class RichException
  object RichException {
    implicit def a(t: Throwable): RichException = new RichException
  }
  class Foo[T]
  def test() {
    val tet: Throwable = null
    def apply[T](x: T*): Foo[T] = null
    val j: Foo[RichException] = /*start*/apply(tet, tet)/*end*/
  }
}
//SCL4282.Foo[SCL4282.RichException]
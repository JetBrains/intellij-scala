object A {
  class Z[T] {
    def m(t: T): T = t
  }

  def foo[T]: Z[T] = null.asInstanceOf[Z[T]]

  def goo[G](z: Z[G]): Z[G] = z

  <ref>goo(foo).m(1)
}
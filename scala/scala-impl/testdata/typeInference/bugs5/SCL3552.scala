object SCL3552 {
  object Foo {
    def update(x: Int, y: Int): Int = x + y
    def update(x: Boolean, y: Boolean): Boolean = x || y
  }

  /*start*/Foo(false) = true/*end*/
}
//Boolean
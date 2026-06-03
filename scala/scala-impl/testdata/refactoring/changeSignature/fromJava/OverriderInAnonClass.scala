object Test {
  val x = new OverriderInAnonClass {
    override def foo(i: Int): Int = i + 2
  }

  x.foo(1)
  x foo 1
}
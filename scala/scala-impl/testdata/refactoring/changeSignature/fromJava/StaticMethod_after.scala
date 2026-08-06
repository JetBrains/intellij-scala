object StaticMethodScala {
  val x = StaticMethod.bar(1, true)

  val f = (i: Int) => StaticMethod.foo(i, true)
}
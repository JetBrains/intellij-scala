object Test {
  val x = new OverriderInAnonClass {
    override def bar(b: Boolean, ii: Int): Boolean = ii + 2
  }

  x.bar(true, 1)
  x.bar(true, 1)
}
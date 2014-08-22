class Child1 extends Overriders {
  override def bar(b: Boolean, ii: Int) = ii + 1

  bar(true, 42)
}
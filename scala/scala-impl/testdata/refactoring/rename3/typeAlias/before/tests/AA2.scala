package tests

object AA2 extends AA {
  override type /*caret*/Type = this.type
}

object AA3 extends A {
  override type /*caret*/Type = this.type

  val x: /*caret*/Type = this
}



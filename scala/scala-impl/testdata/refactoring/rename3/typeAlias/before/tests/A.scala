package tests

class AA extends A {
  override type /*caret*/Type = this.type
}

trait A {
  type /*caret*/Type
}
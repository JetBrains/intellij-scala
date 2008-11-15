package test

trait B {
  def foo: Int
}

class traitSuper extends B {
  def <caret>foo: Int = 0
}


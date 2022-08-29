class Parameterless {
  def <caret>foo() = 1

  foo()
  foo
  this foo ()
  this foo
}

class Child1 extends Parameterless {
  override val foo = 2

  foo
}

class Child2 extends Parameterless {
  override var foo = 3

  foo
}

class Child3 extends Parameterless {
  override var foo = 4

  foo
}
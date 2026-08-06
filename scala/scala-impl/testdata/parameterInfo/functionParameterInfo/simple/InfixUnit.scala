class A {
  def foo() = 1
}

(new A) foo (<caret>)
//TEXT: <no parameters>, STRIKEOUT: false
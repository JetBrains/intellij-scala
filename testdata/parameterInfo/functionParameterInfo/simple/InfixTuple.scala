class A {
  def foo(x: Int, y: Int) = x + y
}

(new A) foo (<caret>1, 2)
//x: Int, y: Int
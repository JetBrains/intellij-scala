class A {
  def foo(x: Int) = 1
}

(new A) foo 1<caret> +1
//TEXT: x: Int, STRIKEOUT: false
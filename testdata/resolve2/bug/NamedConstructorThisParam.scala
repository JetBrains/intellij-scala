class A(x: Int) {
  def this(s: String) = {
    this(s.length)
  }
}

new A(/* */s = "text")
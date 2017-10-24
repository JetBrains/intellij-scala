object A {
  def bar(x : String)(y : String = /* resolved: true */x) { }
}
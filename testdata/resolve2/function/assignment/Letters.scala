class C {
  def foo(n: Int): C = new C
}

var v = new C
v /* resolved: false */ foo= 1



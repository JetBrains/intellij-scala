class C {
  def f(n: Int): C = new C
}

var v = new C
v /* resolved: false */ f= 1



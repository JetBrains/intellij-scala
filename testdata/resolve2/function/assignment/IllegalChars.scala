class C {
  def f_<(n: Int): C = new C
}

var v = new C
v /* resolved: false */ f_<= 1
v /* */ f_< 1
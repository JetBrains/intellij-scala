class C {
  def >(n: Int): C = new C
  def <(n: Int): C = new C
  def !(n: Int): C = new C
}

var v = new C
v /* resolved: false */ >= 1
v /* resolved: false */ <= 1


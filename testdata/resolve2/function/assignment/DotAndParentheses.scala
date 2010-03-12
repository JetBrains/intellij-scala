class C {
  def +(n: Int): C = new C
}

var v = new C
v./* line: 2, name: + */ +=(1)

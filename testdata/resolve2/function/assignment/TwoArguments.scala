class C {
  def +(a: Int, b: Int): C = new C
}

var v = new C
v /* line: 2, name: +, applicable: false */ += 1
v /* line: 2, name: + */ += (1, 2)



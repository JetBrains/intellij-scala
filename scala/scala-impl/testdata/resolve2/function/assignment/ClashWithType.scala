class C {
  def +(n: Int): C = new C
	def +=(s: String) {}
}

var v = new C
v /* line: 3, applicable: false */ += 1

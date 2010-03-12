class C {
  def +(n: Int): C = new C
	def +=(n: Int) {}
}

var v = new C
v /* line: 3 */ += 1

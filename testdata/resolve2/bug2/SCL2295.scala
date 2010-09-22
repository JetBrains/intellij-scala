class Bounds[A, B <: A] {
  def m1(x: String) {}
	def m1(a: A) {}
	def m2(b: B) {
		/* line: 3 */m1(b)	// expected A, actual: B
	}
}
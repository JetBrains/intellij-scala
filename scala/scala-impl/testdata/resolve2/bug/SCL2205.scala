object O extends Application {
	class C {
		def doSmth(p: Int): Int = 123
	}

	def m(p: {def doSmth(p: Int): Int}) {}
  def m(x: Boolean) {}

	/* line: 6 */m(new C)					// error: type mismatch
	/* line: 6 */m(new C{override def doSmth(p: Int) = 1})	// ok
}
class Some {
	def this(p: Int) {
		this()
	}
	val v = new /*caret*/Some(123)	// apply the refactoring here
}
/*
class NameAfterRename {
	def this(p: Int) {
		this()
	}
	val v = new /*caret*/NameAfterRename(123)	// apply the refactoring here
}
*/
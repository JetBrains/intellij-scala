object Days extends Enumeration {
	type Day = Value
	val Monday, Tuesday, Sunday = Value
}

def m(p: Days.Day) {}
def m(X: Int) = 1
/* line: 6 */m(Days.Monday)
class Outer {
	class Inner
	def m(p : Outer#Inner) {}
	m(/*start*/""/*end*/)
}
object Outer {
	implicit def convert[T](p: T): Outer#Inner = {
		val outer = new Outer
		val inner: Outer#Inner = new outer.Inner
		inner
	}
}
/*
Seq(augmentString,
    convert,
    wrapString,
    any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    any2stringfmt),
Some(convert)
*/
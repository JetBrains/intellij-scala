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
Seq(ArrowAssoc,
    Ensuring,
    StringFormat,
    augmentString,
    convert,
    wrapString,
    any2stringadd),
Some(convert)
*/
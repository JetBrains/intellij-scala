class Parent
object Parent {
	implicit def convert[T](p: T) = new Child
}

class Child extends Parent {
	def m(p: Child) {}
	m(/*start*/""/*end*/)
}
/*
Seq(any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    any2stringfmt,
    augmentString,
    convert,
    wrapString),
Some(convert)
*/
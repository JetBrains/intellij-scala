class Parent
object Parent {
	implicit def convert[T](p: T) = new Child
}

class Child extends Parent {
	def m(p: Child) {}
	m(/*start*/""/*end*/)
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
class Foo {
	private[this] def f {}
}

object Foo {
	println(new Foo()./* line: 6, accessible: false */f)
}

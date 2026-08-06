object Foo {
	private[this] def f {}
}

class Foo {
	println(Foo./* line: 2, accessible: false */f)
}


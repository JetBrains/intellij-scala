class Foo {
	private def f {}
}

object Foo {
	println(new Foo()./* line: 6 */f)
}

object Bar {
  println(new Foo()./* line: 6, accessible: false */f)
}


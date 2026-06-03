class Foo {
	private def f {}
}

object Foo {
	println(new Foo()./* line: 2 */f)
}

object Bar {
  println(new Foo()./* line: 2, accessible: false */f)
}


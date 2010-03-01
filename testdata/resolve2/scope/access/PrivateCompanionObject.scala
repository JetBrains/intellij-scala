object Foo {
	private def f {}
}

class Foo {
	println(Foo./* line: 6 */f)
}

class Bar {
  println(Foo./* line: 6, accessible: false */f)
}



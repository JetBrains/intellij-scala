object Foo {
	private def f {}
}

class Foo {
	println(Foo./* line: 2 */f)
}

class Bar {
  println(Foo./* line: 2, accessible: false */f)
}



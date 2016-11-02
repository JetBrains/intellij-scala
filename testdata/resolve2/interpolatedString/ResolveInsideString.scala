val blah = 1

object A {
  val i = 100500

  val a = s"blah = ${ /*resolved: true*/ a}"
  val b = f"blah blah ${ /*resolved: true*/ a}%s"

  val c = s"""blah blah ${
    def foo: Int =  /*resolved: true*/ i

     /*resolved: true, applicable: false*/ foo()
  }"""
}


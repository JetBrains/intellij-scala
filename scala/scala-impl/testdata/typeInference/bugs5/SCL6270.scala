object Funcs {
  val foo: String => Int = _.length
  def foo(s: String): String = s
}


object GCR {
  val s: String = /*start*/Funcs.foo("blah")/*end*/
}
//String
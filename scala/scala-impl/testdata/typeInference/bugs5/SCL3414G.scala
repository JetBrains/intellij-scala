class P {
  def foo: String = ""
}

class Z extends P {
  override def foo = foo
  val x: String = /*start*/foo/*end*/
}
//String
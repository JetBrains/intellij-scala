class P {
  protected def f = {}
}

class C extends P {
  override def f = {}
  
  println(/* line: 6 */ f)
  println(this./* line: 6 */ f)
  println(super./* line: 2 */ f)
}
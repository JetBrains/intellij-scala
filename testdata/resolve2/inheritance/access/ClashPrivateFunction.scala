class P {
  private def f = {}
}

class C extends P {
  def f = {}
  
  println(/* line: 6 */ f)
  println(this./* line: 6 */ f)
  println(super./* resolved: false */ f)
}
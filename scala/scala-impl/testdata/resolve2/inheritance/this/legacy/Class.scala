class P {
  def f = {}
}

class C extends P {
  println(this./* offset: 16 */f)
}
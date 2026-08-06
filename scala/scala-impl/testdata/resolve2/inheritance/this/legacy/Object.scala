class P {
  def f = {}
}

object C extends P {
  println(this./* offset: 16 */f)
}
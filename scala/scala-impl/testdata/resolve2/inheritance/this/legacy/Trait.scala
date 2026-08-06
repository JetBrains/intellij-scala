trait P {
  def f = {}
}

trait C extends P {
  println(this./* offset: 16 */f)
}
class P {
  def f = {"P"}
}

object C extends P {
  override def f = {"C"}
  println(/* offset: 65 */f)
  println(this./* offset: 65 */f)
}

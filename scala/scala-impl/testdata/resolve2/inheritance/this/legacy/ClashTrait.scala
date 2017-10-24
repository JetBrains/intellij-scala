trait P {
  def f = {"P"}
}

trait C extends P {
  override def f = {"C"}
  println(/* offset: 64 */f)
  println(this./* offset: 64 */f)
}

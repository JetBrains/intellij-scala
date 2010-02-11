class O {
  def f = {}
  class I {
    println(/* offset: 16 */f)
    println(this./* resolved: false */f)
  }
}
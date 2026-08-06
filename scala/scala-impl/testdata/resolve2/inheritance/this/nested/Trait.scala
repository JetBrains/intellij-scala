trait O {
  def f = {}
  trait I {
    println(/* offset: 16 */f)
    println(this./* resolved: false */f)
  }
}
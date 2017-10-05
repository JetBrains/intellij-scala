trait O {
  def f = {}
  trait I {
    println(I.this./* resolved: false */f)
    println(O.this./* offset: 16 */f)
  }
}
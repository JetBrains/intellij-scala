class O {
  def f = {}
  class I {
    println(Int.this./* resolved: false */f)
    println(I.this./* resolved: false */f)
    println(O.this./* offset: 16 */f)
  }
}
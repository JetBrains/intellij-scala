class O {
  def f = {}
  class I {
    def f = {}
    println(this./* offset: 43 */f)
    println(O.this./* offset: 16 */f)
  }
}
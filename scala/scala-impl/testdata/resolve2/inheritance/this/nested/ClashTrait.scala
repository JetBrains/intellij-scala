trait O {
  def f = {}
  trait I {
    def f = {}
    println(this./* offset: 43 */f)
    println(O.this./* offset: 16 */f)
  }
}
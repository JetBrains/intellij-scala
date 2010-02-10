object O {
  def f = {}
  object I {
    def f = {}
    println(this./* offset: 45 */f)
    println(O.this./* offset: 17 */f)
  }
}
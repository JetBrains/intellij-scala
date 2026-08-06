object O {
  def f = {}
  object I {
    println(/* offset: 17 */f)
    println(this./* resolved: false */f)
  }
}
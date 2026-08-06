class O {
  def f = {}
  class I {
    def f = {}
    println(/* resolved: false */Int.this./* resolved: false */f)
  }
}
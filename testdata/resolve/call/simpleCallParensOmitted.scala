object SimpleCallParensOmitted {
  def a() = new {
    def b() = 1
  }
  val test = a.<ref>b()
}
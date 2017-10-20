case class CC {
  def f = {}
}

val v = CC

println(v./* resolved: false */f)

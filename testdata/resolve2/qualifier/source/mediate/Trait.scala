trait T {
  def f = {}
}

val v = new T {}

println(v./* line: 2 */f)

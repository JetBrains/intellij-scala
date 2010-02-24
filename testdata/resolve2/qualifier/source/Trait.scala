trait T {
  def f = {}
}

val q: T = new T {}

println(q./* line: 2 */f)
println(/* resolved: false */T.f)
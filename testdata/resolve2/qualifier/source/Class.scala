class C {
  def f = {}
}

val q: C = new C

println(q./* line: 2 */f)
println(/* resolved: false */C.f)
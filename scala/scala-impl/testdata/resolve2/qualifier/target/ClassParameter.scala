class C(p: Int)

println(C./* resolved: false */p)

val q: C = new C(1)
println(q./* resolved: false */p)

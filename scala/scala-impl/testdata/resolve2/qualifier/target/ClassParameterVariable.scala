class C(val p: Int)

println(C./* resolved: false */p)

val q: C = new C(1)
println(q./* line: 1*/p)

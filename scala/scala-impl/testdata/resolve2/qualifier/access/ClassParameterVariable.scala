class C(private var p: Int)

val q: C = new C(1)
println(q./* accessible: false */p)

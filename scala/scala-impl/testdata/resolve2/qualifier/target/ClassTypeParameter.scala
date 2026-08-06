class C[T]

println(C./* resolved: false */T)

val q: C = new C[Int]
println(q./* resolved: false */T)

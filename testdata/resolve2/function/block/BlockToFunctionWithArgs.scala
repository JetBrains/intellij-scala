def f(f: Int => Unit) = {}

println(/* applicable: false */f {})
println(/* */f {p: Int => })
println(/* */f {p: Int => p })
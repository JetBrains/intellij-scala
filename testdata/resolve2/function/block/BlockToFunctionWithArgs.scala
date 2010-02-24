def f(f: Int => Unit) = {}

println(/* valid: false */f {})
println(/* */f {p: Int => })
println(/* */f {p: Int => p })
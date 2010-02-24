def f(f: => Int) = {}

println(/* valid: false */f {})
println(/* */f {1})
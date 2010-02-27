def f(f: => Int) = {}

println(/* applicable: false */f {})
println(/* */f {1})
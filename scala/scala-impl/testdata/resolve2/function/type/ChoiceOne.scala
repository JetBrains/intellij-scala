def f(a: Int, b: String) = {}
def f(a: String, b: Int) = {}

println(/* offset: 4 */ f(1, ""))
println(/* offset: 34 */ f("", 2))
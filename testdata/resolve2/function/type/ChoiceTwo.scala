def f(a: String, b: Int) = {}
def f(a: Int, b: String) = {}

println(/* offset: 34 */ f(1, ""))
println(/* offset: 4 */ f("", 2))
def f(a: Int) = {}
def f(a: Int, b: Int) = {}

println(/* offset: 4 */ f(1))
println(/* offset: 23 */ f(1, 2))
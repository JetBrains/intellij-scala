def f(a: String => Unit, b: String) {}
def f(a: Int => Unit, b: Int) {}

println(/* offset: 4 */f((_: String) => (), ""))
println(/* offset: 43 */f((_: Int) => (), 1))


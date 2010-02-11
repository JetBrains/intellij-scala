def f(p: String => Unit) {}
def f(p: Int => Unit) {}

println(/* resolved: false */f((_: String) => ()))
println(/* resolved: false */f((_: Int) => ()))



def f(a: String => Unit, b: String) {}
def f(a: Int => Unit, b: Int) {}

def a(a: String) {}
def b(a: Int) {}

println(/* offset: 4 */f(a(_), ""))
println(/* offset: 43 */f(b(_), 1))


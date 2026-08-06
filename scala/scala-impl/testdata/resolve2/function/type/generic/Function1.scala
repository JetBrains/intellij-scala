def f(p: String => Unit) {}
def f(p: Int => Unit)(x: String) {}

def a(p: String) {}
def b(p: Int) {}

println(/* line: 1 */f(a(_)))
println(/* line: 2 */f(b(_))(""))
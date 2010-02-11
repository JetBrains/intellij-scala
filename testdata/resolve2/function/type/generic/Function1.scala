def f(p: String => Unit) {}
def f(p: Int => Unit) {}

def a(p: String) {}
def b(p: Int) {}

println(/* resolved: false */f(a(_)))
println(/* resolved: false */f(b(_)))


class C1
class C2 extends C1
class C3 extends C2

def f(a: C1) = {}
def f(a: C2) = {}
def f(a: C3) = {}

println(/* offset: 54 */ f(new C1))
println(/* offset: 72 */ f(new C2))
println(/* offset: 90 */ f(new C3))
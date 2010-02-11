class P
class C extends P

def f(p: P) = {}

println(/* offset: 31 */ f(_: C))
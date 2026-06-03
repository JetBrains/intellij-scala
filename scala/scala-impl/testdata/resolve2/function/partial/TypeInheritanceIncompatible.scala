class P
class C extends P

def f(p: C) = {}

println(/* offset: 31, applicable: false */ f(_: P))
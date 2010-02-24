class P
class C extends P

def f(p: C) = {}

println(/* offset: 31, valid: false */ f(_: P))
class C

def f(implicit p: C) = {}

object O extends C

println(/* offset: 13, applicable: false */ f)


class C

def f(implicit p: C) = {}

implicit object O extends C

println(/* offset: 13 */ f)


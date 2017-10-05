class P
class C extends P

def f(implicit p: P) = {}

implicit val a: P = new P
implicit val b: C = new C

println(/* offset: 31 */ f)
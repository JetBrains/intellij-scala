class M[O <: M[O]]

class Q[O <: M[O]]

class B[O <: M[O]] extends Q[O]

def foo[O <: M[O]](): B[O] = null
val x: Q[String] = /*start*/foo()/*end*/
//B[String]
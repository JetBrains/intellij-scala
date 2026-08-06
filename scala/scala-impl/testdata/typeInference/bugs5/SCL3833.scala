class M[O <: M[O]]

class Q[O <: M[O]]

class B[O <: M[O]] extends Q[O]

class Z extends M[Z]

def foo[O <: M[O]](): B[O] = null
val x: Q[Z] = /*start*/foo()/*end*/
//B[Z]
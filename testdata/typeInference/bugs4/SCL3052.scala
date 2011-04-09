trait M[_]

def bar[X](x: X): M[X] = error("")
val x: M[Double] = /*start*/bar(0)/*end*/

// M[Double]
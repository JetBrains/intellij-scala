trait M[_]
def bar[X](x: X): M[X] = error("")
implicit def str2Double(a : String) = 0d
val y : M[Double] = /*start*/bar("")/*end*/
//M[Double]
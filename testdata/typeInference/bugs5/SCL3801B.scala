case class A(x: Int)
def foo(f: Int => A) = 1
def foo(z: A) = false
/*start*/foo(A)/*end*/
//Int
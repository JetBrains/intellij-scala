()
object O {
def foo(x: Int, y: Int) = 1
def foo(x: Int, y: Int*) = false

/*start*/foo(1, 2)/*end*/
}
//Int
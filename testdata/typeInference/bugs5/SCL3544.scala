class A
trait B

val x: A with B = new A with B

/*start*/x.synchronized {
  1
}/*end*/
//Int
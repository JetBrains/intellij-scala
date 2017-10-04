trait C[A, A] {
  println(/* resolved: false */ A.getClass)
  val vA: /* resolved: false  */ A
  println(classOf[/* resolved: false */ A])
}
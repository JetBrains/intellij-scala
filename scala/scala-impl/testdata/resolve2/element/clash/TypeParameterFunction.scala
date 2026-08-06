def f[A, A] = {
  println(/* resolved: false */ A.getClass)
  var vA: /* resolved: false  */ A = null.asInstanceOf[A]
  println(classOf[/* resolved: false */ A])
}
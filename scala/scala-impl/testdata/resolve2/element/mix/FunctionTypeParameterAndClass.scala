def f[P] {
  class P

  println(/* resolved: false */ P.getClass)
  println(classOf[/* offset: 19 */ P])
}
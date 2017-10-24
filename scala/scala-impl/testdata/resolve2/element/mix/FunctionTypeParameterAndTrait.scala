def f[P] {
  trait P

  println(/* resolved: false */ P.getClass)
  println(classOf[/* offset: 19 */ P])
}
def f[P] {
  val P: String = ""

  println(/* offset: 17 */ P.getClass)
  var vA: /* offset: 6 */ P = null.asInstanceOf[P]
}
def f(p: String) {
  val p: String = ""

  println(/* offset: 25 */ p.getClass)
  println(classOf[/* resolved: false */ p])
}
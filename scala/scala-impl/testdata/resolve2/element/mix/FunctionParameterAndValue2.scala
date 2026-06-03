def f(p: Int) {
  val p: String = ""

  println(/* offset: 22 */ p.getClass)
  println(classOf[/* resolved: false */ p])
}
def f(p: String) {
  var p: String = ""

  println(/* offset: 25 */ p.getClass)
  println(classOf[/* resolved: false */ p])
}
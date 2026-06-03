def f(p: String) {
  object p

  println(/* offset: 28 */ p.getClass)
  println(classOf[/* resolved: false */ p])
}
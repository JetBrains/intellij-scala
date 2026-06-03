class C(a: String, a: String) {
  println(/* resolved: false */ a.getClass)
  println(classOf[/* resolved: false */ a])

  println(/* resolved: false */ b.getClass)
  println(classOf[/* resolved: false */ b])
}
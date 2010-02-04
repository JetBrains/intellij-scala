class C(a: String, a: String) {
  println(/* resolved: false */ a.getClass)
  println(/* resolved: false */ b.getClass)
  println(classOf[/* resolved: false */ a])
  println(classOf[/* resolved: false */ b])
}
class C(a: Int) {
  val a: Int = 1
  println(/* resolved: false */ a.getClass)
}
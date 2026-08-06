class C(a: Int) {
  def a: Int = 1
  println(/* resolved: false */ a.getClass)
}
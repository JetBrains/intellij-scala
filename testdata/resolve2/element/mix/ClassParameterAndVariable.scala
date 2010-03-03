class C(a: Int) {
  var a: Int = 1
  println(/* resolved: false */ a.getClass)
}
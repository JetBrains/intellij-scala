class C(a: Int) {
  var a: Int = 1
  //this case is same as for two vals
  println(/* resolved: true */ a.getClass)
}
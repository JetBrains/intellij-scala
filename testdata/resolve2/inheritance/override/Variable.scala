class C1 {
  var v = {}
}

class C2 extends C1 {
  override var v = {}

  println(/* line: 6 */ v)
}

class P {
  protected def f = {}
}

class C extends P {
  println(/* line: 2 */ f)
}
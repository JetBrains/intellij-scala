class P {
  private def f = {}
}

class C extends P {
  println(/* resolved: false */ f)
}
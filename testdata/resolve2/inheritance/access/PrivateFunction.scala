class P {
  private def f = {}
}

class C extends P {
  println(/* accessible: false */ f)
}
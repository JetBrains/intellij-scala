class P {
  def println(args: String*) {System.out.println("1")}
}

class C extends P {
  /* line: 2 */ println("2")
}

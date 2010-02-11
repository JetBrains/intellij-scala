class P {
  def println(args: String*) {System.out.println("1")}
}

class C extends P {
  /* file: Predef */ println("2")
}

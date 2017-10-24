object Test {
  implicit class Foo(val sc: StringContext) {
    def foo(args: Any*) = macro xxx // comment out macro, it works
  }
  StringContext(str).foo()
  /*resolved: true*/foo"" // good code red

  implicit def RichStringContext(sc: StringContext): RSC = ???
  trait RSC {
    def rich(args: Any*)
  }
  /*resolved: true*/rich""
}

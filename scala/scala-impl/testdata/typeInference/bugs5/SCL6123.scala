object SCL6123 {
class Bar
class Foo extends Dynamic {
  def selectDynamic(name: String): Bar = ???
}

val foo = new Foo()

/*start*/foo.bar/*end*/ -> 42  // Good code red
}
//SCL6123.Bar
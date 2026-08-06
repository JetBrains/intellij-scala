package a3 {
  class Foo {
    import b3.C
    println(/* */C.getClass)
    println(classOf[/* line: 8 */C])
  }
  package b3 {
    case class C
  }
}
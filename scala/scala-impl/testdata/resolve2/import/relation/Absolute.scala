package a1 {
  class Foo {
    import a1.b1.C
    println(/* */C.getClass)
    println(classOf[/* line: 8 */C])
  }
  package b1 {
    case class C
  }
}
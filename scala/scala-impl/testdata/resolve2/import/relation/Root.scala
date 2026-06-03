package a4 {
  class Foo {
    import _root_.a4.b4.C
    println(/* */C.getClass)
    println(classOf[/* line: 8 */C])
  }
  package b4 {
    case class C
  }
}
package a2 {
  class Foo1 {
    import a2.b2.C
    println(/* */C.getClass)
    println(classOf[/* line: 17 */C])
  }
  class Foo2 {
    import _root_.a2.b2.C
    println(/* */C.getClass)
    println(classOf[/* line: 13 */C])
  }
  package b2 {
    case class C
  }
  package a2 {
    package b2 {
      case class C
    }
  }
}
package a2 {
  class Foo1 {
    import a2.b2.C
    println(/* offset: 345 */C.getClass)
    println(classOf[/* offset: 345 */C])
  }
  class Foo2 {
    import _root_.a2.b2.C
    println(/* offset: 290 */C.getClass)
    println(classOf[/* offset: 290 */C])
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
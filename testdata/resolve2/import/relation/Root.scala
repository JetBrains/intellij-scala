package a4 {
  class Foo {
    import _root_.a4.b4.C
    println(/* offset: 169 */C.getClass)
    println(classOf[/* offset: 169 */C])
  }
  package b4 {
    case class C
  }
}
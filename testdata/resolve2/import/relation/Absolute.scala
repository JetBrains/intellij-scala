package a1 {
  class Foo {
    import a1.b1.C
    println(/* offset: 162 */C.getClass)
    println(classOf[/* offset: 162 */C])
  }
  package b1 {
    case class C
  }
}
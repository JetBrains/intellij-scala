package a3 {
  class Foo {
    import b3.C
    println(/* offset: 159 */C.getClass)
    println(classOf[/* offset: 159 */C])
  }
  package b3 {
    case class C
  }
}
package p1 {
  package p2 {
    case class Foo
  }
}

class C {
  import p1._

  println(/* */p1./* */p2./* offset: 43, path: p1.p2.Foo */ Foo.getClass)
  println(classOf[/* */p1./* */p2./* offset: 43, path: p1.p2.Foo */ Foo])

  println(/* */p2./* offset: 43, path: p1.p2.Foo */ Foo.getClass)
  println(classOf[/* */p2./* offset: 43, path: p1.p2.Foo */ Foo])

  println(/* resolved: false */Foo.getClass)
  println(classOf[/* resolved: false */Foo])
}
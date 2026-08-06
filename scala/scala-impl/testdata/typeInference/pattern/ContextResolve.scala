package a {
  class A
}

package b {
  import a.A

  case class B(x: A)

  object Main {
    /*start*/B.unapply(new B(new A))/*end*/
  }
}
//Option[A]
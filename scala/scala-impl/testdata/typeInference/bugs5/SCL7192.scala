object SCL7192 {
  object A {
    class User
  }
  object B {
    class User
    class M
  }

  object C {
    import A._
    import B.{User, _}

    val x: User = new User
    val y: M = new M
    /*start*/x/*end*/
  }
}
//SCL7192.B.User
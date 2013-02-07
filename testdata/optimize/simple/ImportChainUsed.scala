object A {
  object B {
    object C {
      class D
    }
  }

  import B._
  import C.D
  val x : D = new D
}
/*
object A {
  object B {
    object C {
      class D
    }
  }

  import B._
  import C.D
  val x : D = new D
}
*/
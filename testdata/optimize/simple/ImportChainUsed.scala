object A {
  object R {
    object C {
      class D
    }
  }

  import R._
  import C.D
  val x : D = new D
}
/*
object A {
  object R {
    object C {
      class D
    }
  }

  import R._
  import C.D
  val x : D = new D
}
*/
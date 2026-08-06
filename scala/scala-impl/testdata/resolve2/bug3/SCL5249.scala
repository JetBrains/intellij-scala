object A {
  trait Z {
    trait S {
      type Session = Int
    }
  }

  trait ZZ extends Z {
    trait S extends super.S {

    }
    val s : S = new S {}
  }

  object ZZ extends ZZ

  import ZZ.s._

  object Test {
    val s: /* line: 4 */Session = 123
  }
}
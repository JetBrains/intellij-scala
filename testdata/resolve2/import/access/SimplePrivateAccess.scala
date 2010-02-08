class SimplePrivateAccess {
  object A {
    private val x = 34
  }

  object B {
    A./* resolved: false */x
  }
}

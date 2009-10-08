class CompanionAccess {
  class A {
    A./*ref*/y
  }

  object A {
    private val y = 45
  }
}
//true
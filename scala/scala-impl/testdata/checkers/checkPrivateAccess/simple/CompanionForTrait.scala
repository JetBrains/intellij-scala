object CompanionForTrait {
  trait A {
    A./*ref*/x
  }
  object A {
    private val x = 34
  }
}
//true
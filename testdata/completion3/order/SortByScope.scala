class SortByScope{
  def foo1: Unit = {
    val v3 = 45
    def foo2: Unit = {
      val v2 = 34
      if (true){
        val v1 = 56
        v<caret>
      }
    }
  }
}
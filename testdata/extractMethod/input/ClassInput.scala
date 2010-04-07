class ClassInput {
  def foo {
    class A
    val g: A = new A
    /*start*/
    g
    /*end*/
  }
}
/*
*/
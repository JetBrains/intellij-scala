/*initInDeclaration*/
class Test {
  def foo() {
    /*start*/1/*end*/
  }
}
/*
/*initInDeclaration*/
class Test {
  var i: Int = 1

  def foo() {
    i
  }
}
*/
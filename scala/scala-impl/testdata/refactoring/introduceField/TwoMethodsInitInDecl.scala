/*initInDeclaration*/
/*replaceAll*/
class Test {
  def foo() {
    1
  }

  def bar() {
    /*start*/1/*end*/
  }
}
/*
/*initInDeclaration*/
/*replaceAll*/
class Test {
  var i: Int = 1

  def foo() {
    i
  }

  def bar() {
    i
  }
}
*/
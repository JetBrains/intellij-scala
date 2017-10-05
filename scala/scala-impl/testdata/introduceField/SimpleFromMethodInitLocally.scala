/*initLocally*/
class Test {
  def foo() {
    /*start*/1/*end*/
  }
}
/*
/*initLocally*/
class Test {
  var i: Int = _

  def foo() {
    i = 1
    i
  }
}
*/
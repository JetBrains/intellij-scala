/*selectedClassNumber = 1*/
/*replaceAll*/
class Test {
  object A {
    /*start*/1/*end*/
  }
  def foo() {
    1
  }
  1
}

/*
/*selectedClassNumber = 1*/
/*replaceAll*/
class Test {
  var i: Int = 1

  object A {
    /*start*/i/*end*/
  }
  def foo() {
    i
  }
  i
}
*/
/*initLocally*/
class Test {
  abstract class A {
    def foo()
  }

  new A() {
    def foo() {
      /*start*/1/*end*/
    }
  }
}
/*
/*initLocally*/
class Test {
  abstract class A {
    def foo()
  }

  new A() {
    var i: Int = _

    def foo() {
      i = 1
      i
    }
  }
}
*/
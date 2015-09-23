class A {
  def newB(x: Int) = new B(x) {
    def foo() {
      print(/* resolved: true */x) // "Cannot resolve symbol x"
    }
  }
}

class B(val x: Int) {
}
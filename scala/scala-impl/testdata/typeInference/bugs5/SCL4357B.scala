object SCL4357B {
class A {
  def apply() = 0
}

class Test(val a: Object) {
}

class Test2(a: A) extends Test(a) {
  /*start*/a/*end*/
}
}
//SCL4357B.A
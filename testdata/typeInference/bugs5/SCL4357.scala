object SCL4357 {
class A {
  def apply() = 0
}

class Test(val a: A) {
}

class Test2(a: A) extends Test(a) {
  /*start*/a()/*end*/
}
}
//Int
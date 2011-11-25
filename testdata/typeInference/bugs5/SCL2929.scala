def foo() = "text"

class A {
  val x = foo _
  
  /*start*/x _/*end*/
}
//() => () => String
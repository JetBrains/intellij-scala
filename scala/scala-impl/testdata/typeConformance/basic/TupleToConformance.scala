class A
class B extends A

val a: Product2[Int, A] = (1, new B)
//True
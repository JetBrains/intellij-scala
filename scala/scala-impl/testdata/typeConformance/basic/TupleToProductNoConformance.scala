class A
class B extends A

val a: Product2[Int, B] = (23, new A)
//False
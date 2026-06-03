class A
class B
val a: (=> A) => B = (x: A) => new B
val b: A => B = a
//False
def foo = {1}

class Z[T]

class C
class D extends C
class B extends C
class A extends B

val x: Z[_ >: A <: C] = new Z[D]
//False
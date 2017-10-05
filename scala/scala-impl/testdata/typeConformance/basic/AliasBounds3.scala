class Z1
class Z2 extends Z1
class Z3 extends Z2
class Z4 extends Z3

type A >: Z4 <: Z1

val x: A = new Z3
//False
class A[T, U]
class B
class C extends B
val x: (A[T, U] forSome {type T <: B}) forSome {type U <: B} = new A[B, B]
//True
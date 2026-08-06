class A[T, U]
class B
class C extends B
val y: A[T, Int] forSome {type T <: B; type U <: B} = new A[B, Int]
val z: A[T, Int] forSome {type T <: B} = y
//True
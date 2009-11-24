class A[+T, -U]
val z: A[T, U] forSome {type T <: Int; type U >: Int} = new A[Int, Int]
val o: A[Int, Float] = z
//False
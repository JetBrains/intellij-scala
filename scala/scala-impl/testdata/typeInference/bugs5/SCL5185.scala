type Callback[T] = Function1[T, Unit]

val x : Callback[Int] = y => println(/*start*/y/*end*/ +  2)
//Int
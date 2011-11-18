trait CanBuildFrom[-From, -Elem, +To]

trait M[X]

object M {
  implicit def cbf[Elem]: CanBuildFrom[M[_], Elem, M[Elem]] = null
}

def breakOut[From, T, To](implicit b: CanBuildFrom[Nothing, T, To]): CanBuildFrom[From, T, To] = null
def map(bf: CanBuildFrom[List[Int], Int, M[Int]]) = ()
map(/*start*/breakOut/*end*/)

// CanBuildFrom[Any, Int, M[Int]]
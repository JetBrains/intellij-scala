object A

object B

def apply(i: Int,
          a: A.type,
          default: Any = ()) = 0

def apply(d: Double,
          b: B.type) = 0

/*line: 5*/ apply(0, A)

trait M[A]

def foo[A]: M[A] = error("")

trait X

(1 match {
  case x =>
    ()
    /*start*/foo/*end*/
}): M[X]

// M[X]
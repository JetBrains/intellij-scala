class Outer[T] {
    class Inner
}

class HigherKind {
    type tconst[T] <: Outer[T]

    def r(a : tconst[String]) = {
        0 match {
            case a : a.<ref>Inner =>
        }
    }
}
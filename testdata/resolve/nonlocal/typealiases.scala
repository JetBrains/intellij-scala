object TypeAliases {
    abstract class A[T] {
        type t <: T
    }

    class B {
        def r(a: A[String]) {
            def rr(aa:  a.t) = aa.<ref>toLowerCase
            6
        }
    }
}
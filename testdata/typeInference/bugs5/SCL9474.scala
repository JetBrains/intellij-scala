object Foo {
  trait Sys[L <: Sys[L]]

  trait SkipMap[E <: Sys[E], A, B] {
    def add(entry: (A, B)): Option[B]
  }

  trait Output[C <: Sys[C]]

  class OutputImpl[S <: Sys[S]](proc: Proc[S]) extends Output[S] {
    import proc.{outputs => map}

    def add(key: String, value: Output[S]): Unit =
      map.add(key -> /*start*/value/*end*/)   // type mismatch here
  }

  trait Proc[J <: Sys[J]] {
    def outputs: SkipMap[J, String, Output[J]]
  }
}

//Foo.Output[J]
trait PartialType[T[_, _], A] {
  type Apply[B] = T[A, B]

  type Flip[B] = T[B, A]
}

trait State[S, A] {
  def state(s: S): (S, A)
}

class FFF[S,B] {
  def r (ft: PartialType[State, S]#Apply[B], s : S) = {
    ft.<ref>state(s)
  }
}
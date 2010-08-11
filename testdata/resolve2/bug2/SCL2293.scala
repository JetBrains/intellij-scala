trait SOE[+A, +Repr <: /* */SOE[A, Repr]] {
  self: Repr =>
}
class NotResolved(i: Int = /* resolved: false */x) { this: NotResolved =>
  val x = 3
}
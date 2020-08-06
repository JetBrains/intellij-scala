object Ticket522 {
  class Term[X]
  object App {
    // i'm hidden
    case class InternalApply[Y, Z](fun: Y => Z, arg: Y) extends Term[Z]

    def apply[Y, Z](fun: Y => Z, arg: Y): Term[Z] =
      new InternalApply[Y, Z](fun, arg)

    def unapply[X](arg: Term[X]): Option[(Y => Z, Y)] forSome { type Y; type Z } =
      arg match {
        case i: InternalApply[y, z] => Some(i.fun, i.arg)
        case _ => None
      }
  }

  App({ x: Int => x }, 5) match {
    case App(arg, a) =>
  }
}
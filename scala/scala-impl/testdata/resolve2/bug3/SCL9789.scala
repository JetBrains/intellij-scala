object SCL9789 {

  sealed trait True extends Bool {
    type If[T <: Up, F <: Up, Up] = T
  }

  sealed trait False extends Bool {
    type If[T <: Up, F <: Up, Up] = F
  }

  sealed trait Bool {
    type If[T <: Up, F <: Up, Up] <: Up
  }

  case class BoolRep[B <: Bool](val value: Boolean)

  def toBoolean[B <: Bool](implicit b: BoolRep[B]) = b.value

  implicit val falseRep: BoolRep[False] = BoolRep[False](false)
  implicit val trueRep: BoolRep[True] = BoolRep[True](true)

  sealed trait Comparison {
    type Match[IfLT <: Up, IfEQ <: Up, IfGT <: Up, Up] <: Up
    type gt = Match[False, False, True, Bool]
    type lt = Match[True, False, False, Bool]
    type eq = Match[False, True, False, Bool]
    type le = Match[True, True, False, Bool]
    type ge = Match[False, True, True, Bool]
  }

  sealed trait EQ extends Comparison {
    type Match[IfLT <: Up, IfEQ <: Up, IfGT <: Up, Up] = IfEQ
  }

  sealed trait Nat {
    type Match[NonZero[N <: Nat] <: Up, IfZero <: Up, Up] <: Up

    type Compare[N <: Nat] <: Comparison
  }

  sealed trait _0 extends Nat {
    type Match[NonZero[N <: Nat] <: Up, IfZero <: Up, Up] = IfZero

    type Compare[N <: Nat] =
    N#Match[ConstLT, EQ, Comparison]

    type ConstLT[A] = EQ
  }

  sealed trait Succ[N <: Nat] extends Nat {
    type Match[NonZero[N <: Nat] <: Up, IfZero <: Up, Up] = NonZero[N]

    type Compare[O <: Nat] = O#Match[N#Compare, EQ, Comparison]
  }

  type _1 = Succ[_0]
  type _2 = Succ[_1]

  def main(args: Array[String]) {
    toBoolean[_1#Compare[_2]#/*resolved: true*/lt] toString
  }
}


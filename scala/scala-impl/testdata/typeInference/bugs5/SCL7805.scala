object SCL7805 {
  class HList
  class ::[+H, +T <: HList] extends HList
  class HNil extends HList
  class TailSwitch[L <: HList, T <: HList, R <: HList] {
    type Out <: HList
  }

  object TailSwitch {
    implicit def tailSwitch[L <: HList, T <: HList, R <: HList, Out0 <: HList]
    (implicit ts: Aux[L, L, T, T, R, HNil, Out0]): TailSwitch[L, T, R] {type Out = Out0} = ???
  }

  trait Aux[L <: HList, LI <: HList, T <: HList, TI <: HList, R <: HList, RI <: HList, Out <: HList]

  object Aux extends Aux2 {
    implicit def terminate1[L <: HList, LI <: HList, T <: HList, TI <: L, R <: HList, RI <: HList]:
    Aux[L, LI, T, TI, R, RI, R] = ???
  }

  class Aux2 {
    implicit def iter1[L <: HList, T <: HList, TH, TT <: HList, R <: HList, RI <: HList, Out <: HList]
    (implicit next: Aux[L, HNil, T, TT, R, RI, Out]): Aux[L, HNil, T, TH :: TT, R, RI, Out] = ???
  }

  val f: TailSwitch[HNil, String :: HNil , HNil] =
    /*start*/TailSwitch.tailSwitch/*end*/
}
/*
SCL7805.TailSwitch[SCL7805.HNil, String :: SCL7805.HNil, SCL7805.HNil] { type Out = SCL7805.HNil }*/
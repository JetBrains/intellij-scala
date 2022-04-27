package types

trait Projection {
  class C1 {
    trait C2 {
      trait C3
    }
  }

  object O1 {
    class C2

    object O2 {
      class C3
    }
  }

  type T1 = C1#C2

  type T2 = C1#C2#C3

  type T3 = O1.C2

  type T4 = O1.O2.C3
}
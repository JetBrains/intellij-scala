package types

trait Projection {
  class C1 {
    class C2 {
      class C3
    }
  }

  object O1 {
    class C2 {
      class C3
    }

    object O2 {
      class C3

      object O3
    }
  }

  type T1 = C1

  type T2 = C1#C2

  type T3 = C1#C2#C3

  type T4 = O1.type

  type T5 = O1.C2

  type T6 = O1.C2#C3

  type T7 = O1.O2.type

  type T8 = O1.O2.C3

  type T9 = O1.O2.O3.type
}
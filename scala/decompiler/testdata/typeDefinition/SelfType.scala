package typeDefinition

trait SelfType {
  class C1/**/ { self => }/**/

  class C2[A]/**/ { self => }/**/

  class C3 { this: Int =>
  }

  class C4 { this: Int with Long =>
  }

  trait T1

  trait T2

  class C5 extends T1 { this: Int =>
  }

  class C6 extends T1 with T2 { this: Int =>
  }

  class C7 extends T1 with T2 { this: Int with Long =>
  }

  class C8 { this: Int =>
  }
}
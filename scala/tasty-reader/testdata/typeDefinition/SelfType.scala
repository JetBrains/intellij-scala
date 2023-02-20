package typeDefinition

trait SelfType {
  class C1/**/ { self => }/**/

  class C2[A]/**/ { self => }/**/

  class C3 { self: Int =>

  }

  class C4 { self: (Int & Long) =>

  }

  trait T1

  trait T2

  class C5 extends T1 { self: Int =>

  }

  class C6 extends T1, T2 { self: Int =>

  }

  class C7 extends T1, T2 { self: (Int & Long) =>

  }

  class C8 { this: Int =>

  }
}
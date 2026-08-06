object FromDependentObject {
  class AA {
    class E {
      def foo = 1
    }
    object E {
      implicit def srt2E(s: String): E = new E
    }

    def goo(x: E) = x
  }

  object AA extends AA

  object Main {
    import AA._
    goo(/*start*/""/*end*/)
  }
}
/*
Seq(augmentString,
    srt2E,
    wrapString,
    any2ArrowAssoc,
    any2Ensuring,
    any2stringadd,
    any2stringfmt),
Some(srt2E)
*/
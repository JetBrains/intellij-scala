object TypeProjection {
  trait Box {
    type T
    def id(t: T): T = t
  }

  class IntBox extends Box {override type T = Int}
  object StringBox extends Box {override type T = String}

  class ParamBox[S] extends Box {override type T = S}

  class Inner /*start*/{
    val intBox: IntBox = new IntBox()
    val i: intBox.type#T = intBox.id(1)
    val i2: intBox.T = intBox.id(1)

    val s: StringBox.T = StringBox.id("")
    val s2: StringBox.type#T = StringBox.id("")

    val pb = new ParamBox[Int]
    val j: Inner.this.pb.T = pb.id(1)
    val j1: Inner.this.pb.type#T = pb.id(1)

    def foo[S](s: S, pb: ParamBox[S]): Unit = {
      val s1: pb.T = pb.id(s)
    }
  }/*end*/
}

/*
object TypeProjection {
  trait Box {
    type T
    def id(t: T): T = t
  }

  class IntBox extends Box {override type T = Int}
  object StringBox extends Box {override type T = String}

  class ParamBox[S] extends Box {override type T = S}

  class Inner /*start*/{
    val intBox: IntBox = new IntBox()
    val i: Int = intBox.id(1)
    val i2: Int = intBox.id(1)

    val s: String = StringBox.id("")
    val s2: String = StringBox.id("")

    val pb = new ParamBox[Int]
    val j: Int = pb.id(1)
    val j1: Int = pb.id(1)

    def foo[S](s: S, pb: ParamBox[S]): Unit = {
      val s1: S = pb.id(s)
    }
  }/*end*/
}
*/
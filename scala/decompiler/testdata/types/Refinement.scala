package types

trait Refinement {
  type T1 = { def member: Int }

  type T2 = { def member1: Int; def member2: Int }

  type T3 = { val value: Int; def method(x: Int): Unit; type Type }

  type T4 = Int { def member: Int }

  type T5 = scala.collection.immutable.Seq[Int] { def member: Int }

  val v1/**//*: { val v1: Int; def f1(x: Int): Unit; type T = String; type C <: AnyRef; type CC <: AnyRef with Product with Serializable; val v2: Int; val Path: { type A }; val v3: this.Path.A; def f4(): Unit }*/ = /**/new {
    val v1: Int = ???

    def f1(x: Int): Unit = ???

    type T = String

    class C

    case class CC()

    object O

    case object CO

    lazy val v2: Int = ???

    val Path: { type A } = ???

    val v3: Path.A = ???

    override def toString: String = ???

    protected def f2(): Unit = ???

    private def f3(): Unit = ???

    @deprecated
    def f4(): Unit = ???
  }/*???*/
}
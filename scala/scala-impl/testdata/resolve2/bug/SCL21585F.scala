object Repro {
  trait Api extends Api1

  trait Api1 {
    self: Api =>
    type X <: XApi

    class XApi

    abstract class XExtractor {
      def unapply(a: X): Option[String]
    }
    def apiX: X = ???
  }

  trait Impl extends Impl1 {

  }

  trait Impl1 extends Api {
    self: Impl =>
    case class X(s: String) extends XApi

    object X extends XCompanion
    val implX: X = apiX
  }

  object Test {
    val impl: Impl = ???
    (null: Any) match {
      case impl.X(s) => s./*resolved: true*/charAt(0)
    }
  }
}


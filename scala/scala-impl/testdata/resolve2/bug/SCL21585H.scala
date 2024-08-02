object Repro {
  trait Api extends Api1

  trait Api1 {
    self: Api =>

    class XApi

    type X <: XApi

    abstract class XCompanion {
      def foo(x: X) = ()
    }
  }

  trait Impl extends Impl1

  trait Impl1 extends Api {
    self: Impl =>
    class X extends XApi

    object X extends XCompanion {
      override def foo(x: X) = ()
      this./*resolved: true*/foo(new impl.X)("")
    }
  }

}


trait C {
  self: Z =>
}

trait K {
  self: Z =>
  import global._
  import definitions._

  val treeInfo.Applied(ff) = new U
  /* line: 18 */f(ff)
}

trait Definitions {
  self : Global =>
  object definitions extends DefinitionsClass
  class DefinitionsClass {
    def f(f: F) : F = new F
    def f(x: Int) = 123
  }

  object treeInfo extends F {

  }
}

trait Test {
  self: Global =>
  class U
  class F {
    object Applied {
      def unapply(u: U): Option[F] = None
    }
  }
}

class Global extends Definitions with Test {

}

trait Z extends K with C {
  val global: Global
}
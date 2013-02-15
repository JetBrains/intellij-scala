trait C {
  self: Z =>
}

trait K {
  self: Z =>
  import global._
  import definitions._
  /* line: 16 */f(new F)
}

trait Definitions {
  self : Global =>
  object definitions extends DefinitionsClass
  class DefinitionsClass {
    def f(f: F) : F = new F
    def f(x: Int) = 123
  }
}

trait Test {
  self: Global =>
  class F
}

class Global extends Definitions with Test {

}

trait Z extends K with C {
  val global: Global
}
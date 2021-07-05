package parameter

trait Qualifier {
  class ClassPrivate(private[parameter] val x: Int)

  class ClassProtected(protected[parameter] val x: Int)

  class TraitPrivate(private[parameter] val x: Int)

  class TraitProtected(protected[parameter] val x: Int)

  enum EnumPrivate(private[parameter] val x: Int) {
    case Case/**/ extends EnumPrivate(1)/**/
  }

  enum EnumProtected(protected[parameter] val x: Int) {
    case Case/**/ extends EnumProtected(1)/**/
  }

  enum EnumCaseClassPrivate {
    case Class(private[parameter] val x: Int)
  }

  enum EnumCaseClassProtected {
    case Class(protected[parameter] val x: Int)
  }

  object Object {
    class Class(private[Object] val x: Int)
  }
}
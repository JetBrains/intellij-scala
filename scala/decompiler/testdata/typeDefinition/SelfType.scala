package typeDefinition

trait SelfType {
  class C1/**/ { self => }/**/

  class C2[A]/**/ { self => }/**/

  class C3 { self: Int =>

  }
}
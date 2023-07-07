package typeDefinition

trait Derivation {
  class Single derives Show

  class Multiple derives Show, Eq

  class Companion derives Show

  object Companion
}/**/

trait Show[A]; object Show { def derived[A]: Show[A] = ??? }

trait Eq[A]; object Eq { def derived[A]: Eq[A] = ??? }
/**/
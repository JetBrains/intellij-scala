package typeDefinition

trait Derivation {
  class Single derives /**//*typeDefinition.*/Show

  class Multiple derives /**//*typeDefinition.*/Show, /**//*typeDefinition.*/Eq

  class Companion derives /**//*typeDefinition.*/Show

  object Companion
}/**/

trait Show[A]; object Show { def derived[A]: Show[A] = ??? }

trait Eq[A]; object Eq { def derived[A]: Eq[A] = ??? }
/**/
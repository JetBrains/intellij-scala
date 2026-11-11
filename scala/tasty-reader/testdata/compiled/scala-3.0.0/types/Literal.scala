package types

trait Literal {
  type T1 = 2147483647

  type T2 = 9223372036854775807L

  type T3 = 3.4028235E38F

  type T4 = 1.7976931348623157E308D

  type T5 = true

  type T6 = false

  type T7 = 'c'

  type T8 = "String"

  object LiteralsToBeExported {
    def f1[T]: T =:= true = ???
  }

  /**/export LiteralsToBeExported.f1/*final def f1[T]: T =:= true = ???*/
}
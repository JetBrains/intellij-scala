import scala.language.implicitConversions

object Example {

  // this implicit class is applied properly by the Scala compiler
  implicit class HigherKindedExampleNotApplied[A[x] <: LowerBound[x]](val example: Example[A]) extends AnyVal {
    def validAccessName: String = example.bound[Unit].name
  }

  // this implicit class is applied in IntelliJ and in the Scala compiler
  implicit class HigherKindedExampleApplied[A[_]](val example: Example[A]) extends AnyVal {
    def invalidAccessName: String = "invalid" // example.bound[Unit].name // but this doesn't compile
  }

  object AExample extends Example[ABound] {
    override def bound[X]: ABound[X] = new ABound[X]
  }

  object BExample extends Example[BBound] {
    override def bound[X]: BBound[X] = new BBound[X]
  }

  BExample.<ref>validAccessName  // highlighted as error in IntelliJ
  BExample.invalidAccessName  // works fine when bounded
}

trait Example[A[x]] {
  def bound[X]: A[X]
}

trait LowerBound[X] {
  def name: String
}

class ABound[X] extends LowerBound[X] {
  override def name: String = "specific"
}

class BBound[X] extends LowerBound[X] {
  override def name: String = "unit"
}
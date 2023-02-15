package parameter

trait DefaultArguments {
  def method(x: Int = /**/1/*???*/): Unit

  class Class(x: Int = /**/1/*???*/)

  class ClassVal(val x: Int = /**/1/*???*/)

  class ClassVar(var x: Int = /**/1/*???*/)

  class ClassPrivateVal(/**/private val /**/x: Int = /**/1/*???*/)

  class ClassProtectedVal(protected val x: Int = /**/1/*???*/)

  class ClassOverrideFinalVal(override final val hashCode: Int = /**/1/*???*/)

  case class CaseClass(x: Int = /**/1/*???*/)
}
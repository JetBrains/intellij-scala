object tag {
  def apply[U] = new Tagger[U]
}

trait Tagged[U]

type WithTag[+T, U] = T with Tagged[U]

class Tagger[U] {
  def apply[T](t: T): WithTag[T, U] = t.asInstanceOf[WithTag[T, U]]
}

sealed trait NoConversion

object NoConversion {

  import scala.language.implicitConversions

  implicit def toNoConversion[T](x: T): WithTag[T, NoConversion] = tag[NoConversion](x)
}

def foo(withTag: WithTag[BigDecimal, NoConversion]) = println()

foo(/*start*/BigDecimal(1.1)/*end*/)

//WithTag[BigDecimal, NoConversion]
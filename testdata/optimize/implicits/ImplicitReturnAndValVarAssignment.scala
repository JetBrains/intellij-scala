trait Scratch {
  case object T
  case object U
  case object V
  case object W

  object Implicits {
    implicit def TToInt(t: T.type): Int = 0
    implicit def UToInt(t: U.type): Int = 0
    implicit def VToInt(t: V.type): Int = 0
    implicit def WToInt(t: W.type): Int = 0
  }
  import Implicits.{TToInt, UToInt, VToInt, WToInt}

  def t: Int = {
    return T
  }

  def u: Int = {
    U
  }

  val v: Int = V

  var w: Int = W
}
/*trait Scratch {
  case object T
  case object U
  case object V
  case object W

  object Implicits {
    implicit def TToInt(t: T.type): Int = 0
    implicit def UToInt(t: U.type): Int = 0
    implicit def VToInt(t: V.type): Int = 0
    implicit def WToInt(t: W.type): Int = 0
  }
  import Implicits.{TToInt, UToInt, VToInt, WToInt}

  def t: Int = {
    return T
  }

  def u: Int = {
    U
  }

  val v: Int = V

  var w: Int = W
}*/
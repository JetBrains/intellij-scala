import scala.xml.Node

object SCL4981 {
  val xml: scala.xml.Elem = <xml></xml>
  lazy val transform: Option[Transform] = xml \ "TRANSFORM" match {
    case Seq() => None
    case Seq(transformNode) => Some(new Transform(/*start*/transformNode/*end*/))
  }

  class Transform(m: Node)
}
//Node
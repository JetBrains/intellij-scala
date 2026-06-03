import scala.xml._
import scala.xml.transform._

val namespaceStrippingRewriteRule = new RewriteRule {
  override def transform(n: Node) = n match {
    case Elem(prefix, label, attributes, scope, children@_*) =>
      Elem(prefix, /*start*/label/*end*/, attributes, TopScope, transform(children): _*)
    case x => x
  }

  override def transform(ns: Seq[Node]): Seq[Node] = ns flatMap transform
}
//String
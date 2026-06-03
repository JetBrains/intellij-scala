case class Entry(key: String, value: String)

trait Index {
  import scala.collection.Searching._
  def entries:Array[Entry]
  implicit val keyOrdering:Ordering[Entry]
  def find(k:String) = entries.<ref>search(Entry(k,""))
}
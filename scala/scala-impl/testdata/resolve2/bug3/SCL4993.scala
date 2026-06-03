
object IdeaBug1 {
  private[this] implicit def toSerFix[K, V](m: Map[K, V]) = new {
    def sf = m map identity
  }

  def main(args: Array[String]) = {
    Map(3 -> 4).toMap./* resolved: true */sf
  }
}
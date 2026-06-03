object K {
  def isNotBlank(s: String): Boolean = true
  val data: Map[String, Any] = Map.empty
  /*start*/List("WFC", "StC", "BBG").map(data.get(_)).flatten.map(_.asInstanceOf[String]).filter(isNotBlank(_))/*end*/
}
//List[String]
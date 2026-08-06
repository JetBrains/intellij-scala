object TestApp extends App {
  def testMatch(s: String) = s match {
    case <ref>TestEnum(e) => println(e)
    case _ => println("Not matched")
  }
}
object BetterMonadicFor {
  def test(): Option[String] = for {
    case implicit0(s: String) <- Some("x")
  } yield s
}

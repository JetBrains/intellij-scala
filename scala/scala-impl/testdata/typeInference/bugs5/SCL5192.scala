Seq("a", "b", "c") match {
  case Seq() => "empty"
  case init :+ last => "last size: " + /*start*/last.length/*end*/ // length is red
}
//Int
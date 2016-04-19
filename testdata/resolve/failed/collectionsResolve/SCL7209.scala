class mc {
  for {
    x <- List(1,2,3)
    y: List[String] = ???
    z = y.<ref>map(_.head) // red because (y:Nothing) is inferred
  } yield ()
}
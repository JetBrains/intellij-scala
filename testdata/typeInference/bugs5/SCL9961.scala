object SCL9961 {
  case class X(x: X)
  case class Y(y: Y)

  object X {
    implicit def XtoY(outer: X): Y = outer match {
      case X(inner) => Y(/*start*/inner/*end*/)
    }
  }
}
//SCL9961.Y
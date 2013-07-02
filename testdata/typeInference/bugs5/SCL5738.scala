object SCL5738 extends App {
  val key = "a"

  val foo = Some(args) collect {
    case Array("1") => Map(key -> 1)
    case _          => Map(key -> "unknown")
  } getOrElse Map.empty

  println(/*start*/foo/*end*/)
}
//Map[String, Any]
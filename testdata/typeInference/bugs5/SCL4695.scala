class SCL4695 {

  type Receive = scala.PartialFunction[scala.Any, scala.Unit]

  def first : Receive = null

  def receive = first orElse {
    case "baz" => Some("baz")
  }
}
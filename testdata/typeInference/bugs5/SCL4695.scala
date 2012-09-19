class SCL4695 {

  type Receive = scala.PartialFunction[scala.Any, scala.Unit]

  def first : Receive = null

  def receive = first orElse /*start*/{
    case "baz" => Some("baz")
  }/*end*/
}
//PartialFunction[Any, Unit]
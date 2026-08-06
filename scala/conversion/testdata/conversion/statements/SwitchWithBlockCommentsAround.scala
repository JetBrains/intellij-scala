object Switch {
  def main(args: Array[String]): Unit = {
    /*block comment 1*/ 42 match {
      case 1 =>
        System.out.println(1)
    } /*block comment 2*/
    /*block comment 1*/
    42 match {
      case 1 =>
        System.out.println(1)
    }
    /*block comment 2*/
  }
}
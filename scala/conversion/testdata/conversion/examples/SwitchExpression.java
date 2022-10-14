/*start*/
class SwitchExpression {
    public void main(String[] args) {
        int x = switch (args.length) {
            case 1 -> 3;
            case 2 -> 4;
            case 3, 4 -> 5;
            default -> args.length * 2;
        };
    }
}/*end*/

/*
class SwitchExpression {
  def main(args: Array[String]): Unit = {
    val x: Int = args.length match {
      case 1 => 3
      case 2 => 4
      case 3 | 4 => 5
      case _ => args.length * 2
    }
  }
}*/
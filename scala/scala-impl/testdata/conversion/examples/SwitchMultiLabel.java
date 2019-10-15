/*start*/
class SwitchMultiLabel {
    public void main(String[] args) {
        int x = switch (args.length) {
            case 1 -> 2;
            case 2, 3 -> 4;
            case 4, 5, 6 -> 7;
            default -> 8;
        };
    }
}/*end*/

/*
class SwitchMultiLabel {
  def main(args: Array[String]): Unit = {
    val x: Int = args.length match {
      case 1 => 2
      case 2 | 3 => 4
      case 4 | 5 | 6 => 7
      case _ => 8
    }
  }
}*/
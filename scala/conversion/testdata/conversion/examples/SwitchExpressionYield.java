class SwitchExpressionYield {
    public void main(String[] args) {
        int x = switch (args.length) {
            case 1 -> {
                if(args[0].equals("foo")) yield 3;
                else {
                    System.out.println("not foo");
                    yield 4;
                }
            }
            default -> {
                System.out.println("Oops");
                yield args.length * 2;
            }
        };
    }
}
/*
class SwitchExpressionYield {
  def main(args: Array[String]): Unit = {
    val x: Int = args.length match {
      case 1 => if (args(0) == "foo") 3
      else {
        System.out.println("not foo")
        4
      }

      case _ => System.out.println("Oops")
        args.length * 2

    }
  }
}*/
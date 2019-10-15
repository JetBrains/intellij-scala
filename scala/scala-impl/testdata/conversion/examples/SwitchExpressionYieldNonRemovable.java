/*start*/
class SwitchExpressionYieldNonRemovable {
    public void main(String[] args) {
        int x = switch (args.length) {
            default -> {
                if(args[0].equals("foo")) yield 5;
                System.out.println("oops");
                yield 6;
            }
        };
    }
}/*end*/

/*
class SwitchExpressionYieldNonRemovable {
  def main(args: Array[String]): Unit = {
    val x: Int = args.length match {
      case _ => if (args(0) == "foo") {
        `yield`
        5 // todo: Java's yield is not supported
      }
        System.out.println("oops")
        6

    }
  }
}*/
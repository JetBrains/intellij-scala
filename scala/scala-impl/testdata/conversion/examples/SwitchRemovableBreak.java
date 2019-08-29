/*start*/
class SwitchExpression {
    public void main(String[] args) {
        switch(args.length) {
            case 0:
                System.out.println("No arguments!");
                break;
            case 1:
                if (args[0].equals("foo")) {
                    System.out.println("Foo is passed");
                    break;
                } else {
                    System.out.println("Single arg is passed (not foo)");
                }
                break;
            case 2: {
                System.out.println("Two args passed");
                break;
            }
            default:
                System.out.println("Many args passed");
                break;
        }
    }
}/*end*/

/*
class SwitchExpression {
  def main(args: Array[String]): Unit = {
    args.length match {
      case 0 =>
        System.out.println("No arguments!")

      case 1 =>
        if (args(0) == "foo") System.out.println("Foo is passed")
        else System.out.println("Single arg is passed (not foo)")

      case 2 =>
        System.out.println("Two args passed")


      case _ =>
        System.out.println("Many args passed")

    }
  }
}*/
class SwitchNonRemovableBreak {
    public void main(String[] args) {
        switch(args.length) {
            case 1:
                if (args[0].equals("foo")) {
                    System.out.println("Foo is passed");
                    break; // unsupported
                }
                System.out.println("Single arg is passed (not foo)");
                break;
        }
    }
}
/*
class SwitchNonRemovableBreak {
  def main(args: Array[String]): Unit = {
    args.length match {
      case 1 =>
        if (args(0) == "foo") {
          System.out.println("Foo is passed")
          break //todo: break is not supported
          // unsupported
        }
        System.out.println("Single arg is passed (not foo)")
    }
  }
}*/
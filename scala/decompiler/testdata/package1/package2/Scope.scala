package package1.package2

trait Scope {
  private[package1] def method1: Int = ???

  private[package2] def method2: Int = ???

  object Object1 {
    private[Object1] def method1: Int = ???

    object Object2 {
      private[Object1] def method1: Int = ???

      private[Object2] def method2: Int = ???
    }
  }

  class Class1 {
    class Class2 {
      private[Class1] def method1: Int = ???

      private[Class2] def method2: Int = ???
    }

    private[Class1] def method1: Int = ???
  }
}
package member

trait ExtensionMethod {
  extension (i: Int)
    def method: Int = ???

  extension (s: String)
    def method1: Int = ???

    def method2: Long = ???

  object Foo {
    extension (s: String)
      def method1: Int = ???

      def method2: Long = ???
  }
}
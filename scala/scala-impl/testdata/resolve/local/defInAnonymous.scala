class DefInAnonymousTest {
  {
    new Runnable {
      def foo = {}
      def bar = <ref>foo
    }
  }
}
package A {

  package best {
  }

  import Test._

  class Test {
    best./* resolved: false*/foo
  }

  object Test {

    object best {
      def foo = ???
    }

  }

}

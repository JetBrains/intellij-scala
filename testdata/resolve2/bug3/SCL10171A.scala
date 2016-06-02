package A {

  package best {
  }

  class Test {

    import Test._

    best./* resolved: true */foo

  }

  object Test {

    object best {
      def foo = ???
    }

  }

}

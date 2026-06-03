object FunnyBusiness {

  trait Converter {
    protected type Foo
    protected def newFoo: Foo
    def convert(i: Int): Foo = newFoo
  }

  object MyConverter extends Converter {
    protected class Foo {
      def hello() {
        println("Hello!")
      }
    }
    def newFoo = new Foo
  }

  MyConverter.newFoo.hello()     // Nothing wrong with this hello, but...
  MyConverter.convert(3)./*line: 11*/hello() // This hello is not recognized (does not appear in autocomplete, can't ctrl-click)

}
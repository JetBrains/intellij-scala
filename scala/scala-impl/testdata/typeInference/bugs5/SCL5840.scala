object SCL5840 {
  import scala.language.experimental.macros
  import scala.reflect.runtime.{universe=>ru}
  import scala.reflect.macros.Context

  class SC {
    def hello : String = "hello"
  }

  object Macros {
    def impl(c:Context)(args:c.Expr[Any]*) : c.Expr[SC] = {
      c.universe.reify { new SC }
    }
  }

  object Test {
    implicit class Foo(val sc:StringContext) {
      def foo(args:Any*) : SC = macro Macros.impl
      def noMacro(args:Any*) = new SC
    }

    /*start*/foo"say hello".hello/*end*/ // Cannot resolve symbol hello
    noMacro"say hello".hello // It works

    def h : SC = macro Macros.impl
    h.hello // It also works

  }
}
//String
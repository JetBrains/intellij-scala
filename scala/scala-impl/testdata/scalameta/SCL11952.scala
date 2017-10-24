import scala.meta._
class SCL11952 extends scala.annotation.StaticAnnotation {
  inline def apply(tree: Any): Any = meta {
    val q"class $tname" = tree
    q"""
       class $tname
       object ${Term.Name(tname.value)} {
        type A = Int
        trait B
        def foo: A = ???
        def bar: B = ???
       }
     """
  }
}
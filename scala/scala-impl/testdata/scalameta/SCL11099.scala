import scala.meta._

class poly extends scala.annotation.StaticAnnotation {
  inline def apply(defn: Any): Any = meta {
    defn match {
      case q"def $name[$param]($in: $inT[$p1]): $outT[$p2] = $impl" =>
        val valName = Pat.Var(Term.Name(name.value))
        q"""
            val $valName: _root_.cats.arrow.FunctionK[$inT, $outT] =
              new _root_.cats.arrow.FunctionK[$inT, $outT] {
                def apply[$param]($in: $inT[$p1]): $outT[$p2] = $impl
              }
          """
    }
  }
}
import com.sun.corba.se.impl.orbutil.graph.Graph

trait CompilationUnits { self : Global =>
  class CompilationUnit
}

class Global extends CompilationUnits {

}

class MainXMLExporter {
  class XMLExporter(val global: Global) {
    import global._

    val units: <ref>CompilationUnit = null
  }

}
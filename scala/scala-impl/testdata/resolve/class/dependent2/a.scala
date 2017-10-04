trait CompilationUnits { self : Global =>
  class CompilationUnit
}

class Global extends CompilationUnits {

}

class MainXMLExporter {
  class XMLExporter() {
    val global = new Global

    import global._

    val units: <ref>CompilationUnit = null
  }

}
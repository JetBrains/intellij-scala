package a {
case class CaseClassObjectStaticImport
object CaseClassObjectStaticImport {
  class H
}

class F {
  import /* */CaseClassObjectStaticImport./* */H
}
}
package foo

private class PrivateTopLevel(val privateClassParam: Int)

private object PrivateTopLevel {
  class PublicInPrivate(val innerClassParam: String)
}

private[foo] class PackagePrivateTopLevel(val packagePrivateClassParam: Int) {
  def publicMember: Int = 0
}

class A1(private[foo] val ctorPrivateParameter: Int) {
  private[foo] def packagePrivateMethod = 0
}

object A1 {
  private[foo] class PackagePrivateClass
  private[foo] type PackagePrivateTypeAlias = Int
}

class A2 private[foo] (val packagePrivateCtorParameter: Int)
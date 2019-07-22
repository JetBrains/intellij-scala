package foo

private class PrivateTopLevel {
}

private object PrivateTopLevel {
  class PublicInPrivate(s: String)
}

private[foo] class PackagePrivateTopLevel

class A1(private[foo] val ctorPrivateParameter: Int) {
  private[foo] def packagePrivateMethod = 0
}

object A1 {
  private[foo] class PackagePrivateClass
  private[foo] type PackagePrivateTypeAlias = Int
}

class A2 private[foo] (val packagePrivateCtorParameter: Int)
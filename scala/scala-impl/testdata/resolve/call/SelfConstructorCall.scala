final class SelfConstructorCall[A] private (private var result: Option[A]) {
  def this(result: A) = <ref>this(Some(result))
  def this() = this(None)
}
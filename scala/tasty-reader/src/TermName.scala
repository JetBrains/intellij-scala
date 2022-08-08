package org.jetbrains.plugins.scala.tasty.reader

// dotty.tools.tasty.TastyFormat.NameTags
// dotty.tools.dotc.core.NameKinds
// dotty.tools.dotc.core.StdNames

private class TermName(private val value: String) extends AnyVal {
  override def toString: String = value

  def asSimpleName: TermName = new TermName(value)
}

// TODO Read structure & tag instead of a plain String?
// TODO Handle specific tags
object TermName {
  val EmptyTermName: TermName = new TermName("<empty>")
  def simpleNameKindOfTag(tag: Int)(name: TermName): TermName = new TermName(name.value + "$")
  def uniqueNameKindOfSeparator(separator: String)(original: TermName, num: Int): TermName = new TermName(separator + num)
  def numberedNameKindOfTag(tag: Int)(name: TermName, num: Int): TermName = new TermName(name.value + "$default$" + num)
  def qualifiedNameKindOfTag(tag: Int)(qualifier: TermName, simpleName: TermName): TermName = new TermName(qualifier.value + "." + simpleName.value)
  def SignedName(original: TermName, sig: String, target: TermName): TermName = new TermName(original.value + sig)
}

package org.jetbrains.plugins.scala.compiler.references.bytecode

/**
 * @param fqn          Fully quialified name of a class.
 * @param superClasses Fully qualified names of all super classes/interfaces.
 */
private[references] final case class ClassInfo(
  isAnonymous:  Boolean,
  fqn:          String,
  superClasses: Set[String]
)

/**
 * Represents a reference to a method or field in compiled class file
 */
private[references] sealed trait MemberReference {

  /**
   * Fqn of an enclosing class
   */
  def owner: String

  /**
   * Field/method name
   */
  def name: String

  /**
   * Line of occurence in source file, or -1 if indeterminable
   */
  def line: Int

  def fqn: String = s"$owner.$name"
}

private final case class FieldReference(
  override val owner: String,
  override val name:  String,
  override val line:  Int
) extends MemberReference

private final case class MethodReference(
  override val owner: String,
  override val name:  String,
  override val line:  Int,
  args:               Int
) extends MemberReference

private[references] final case class FunExprInheritor(
  interface: String,
  line:      Int
)

private[references] sealed trait ParsedClass {
  def classInfo: ClassInfo
  def refs: Seq[MemberReference]
  def funExprs: Seq[FunExprInheritor]
}

private final case class RegularClass(
  override val classInfo: ClassInfo,
  override val refs:      Seq[MemberReference],
  override val funExprs:  Seq[FunExprInheritor],
) extends ParsedClass

private final case class FunExprClass(
  override val classInfo: ClassInfo,
  override val refs:      Seq[MemberReference],
  line:                   Int
) extends ParsedClass {
  override def funExprs: Seq[FunExprInheritor] = Seq.empty
}

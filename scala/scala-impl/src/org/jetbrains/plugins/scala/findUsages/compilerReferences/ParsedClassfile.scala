package org.jetbrains.plugins.scala.findUsages.compilerReferences

/**
 * @param fqn          Fully quialified name of a class.
 * @param superClasses Fully qualified names of all super classes/interfaces.
 */
private final case class ClassInfo(
  fqn: String,
  superClasses: Set[String]
)

/**
 * Represents a reference to a method or field in compiled class file
 */
private sealed trait MemberReference {
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
  
  def fullName: String = s"$owner.$name"
}

private final case class FieldReference(
  override val owner: String,
  override val name: String,
  override val line: Int
) extends MemberReference

private final case class MethodReference(
  override val owner: String,
  override val name: String,
  override val line: Int
) extends MemberReference

private final case class ParsedClassfile(
  classInfo: ClassInfo,
  refs: Seq[MemberReference]
)

package org.jetbrains.plugins.scala.lang.typeInference

class TypeVariableTest extends TypeInferenceTestBase {
  private final val Prefix =
    "class Type; " +
    "class Invariant[A]; class Covariant[+A]; class Contravariant[-A]; " +
    "class InvariantLowerBound[A >: Type]; class CovariantLowerBound[+A >: Type]; class ContravariantLowerBound[-A >: Type]; " +
    "class InvariantUpperBound[A <: Type]; class CovariantUpperBound[+A <: Type]; class ContravariantUpperBound[-A <: Type]; "

  // Variance

  def testInvariance1(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Invariant[Type]) match { case _: Invariant[t] => val v: Type = ??? : t }")

  def testInvariance2(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : Invariant[Type]) match { case _: Invariant[t] => val v: t = ??? : Type }")

  def testCovariance1(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Covariant[Type]) match { case _: Covariant[t] => val v: Type = ??? : t }")

  def testCovariance2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Covariant[Type]) match { case _: Covariant[t] => val v: t = ??? : ${CARET}Type }")

  def testContravariance1(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Contravariant[Type]) match { case _: Contravariant[t] => val v: Type = ??? : ${CARET}t }")

  def testContravariance2(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : Contravariant[Type]) match { case _: Contravariant[t] => val v: t = ??? : Type }")

  // Parameter bounds

  def testInvariantLowerBound1(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : InvariantLowerBound[_]) match { case _: InvariantLowerBound[t] => val v: Type = ??? : ${CARET}t }")

  def testInvariantLowerBound2(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : InvariantLowerBound[_]) match { case _: InvariantLowerBound[t] => val v: t = ??? : Type }")

  def testInvariantUpperBound1(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : InvariantUpperBound[_]) match { case _: InvariantUpperBound[t] => val v: Type = ??? : t }")

  def testInvariantUpperBound2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : InvariantUpperBound[_]) match { case _: InvariantUpperBound[t] => val v: t = ??? : ${CARET}Type }")

  def testCovariantLowerBound1(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : CovariantLowerBound[_]) match { case _: CovariantLowerBound[t] => val v: Type = ??? : ${CARET}t }")

  def testCovariantLowerBound2(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : CovariantLowerBound[_]) match { case _: CovariantLowerBound[t] => val v: t = ??? : Type }")

  def testCovariantUpperBound1(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : CovariantUpperBound[_]) match { case _: CovariantUpperBound[t] => val v: Type = ??? : t }")

  def testCovariantUpperBound2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : CovariantUpperBound[_]) match { case _: CovariantUpperBound[t] => val v: t = ??? : ${CARET}Type }")


  def testContravariantLowerBound1(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : ContravariantLowerBound[_]) match { case _: ContravariantLowerBound[t] => val v: Type = ??? : ${CARET}t }")

  def testContravariantLowerBound2(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : ContravariantLowerBound[_]) match { case _: ContravariantLowerBound[t] => val v: t = ??? : Type }")

  def testContravariantUpperBound1(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : ContravariantUpperBound[_]) match { case _: ContravariantUpperBound[t] => val v: Type = ??? : t }")

  def testContravariantUpperBound2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : ContravariantUpperBound[_]) match { case _: ContravariantUpperBound[t] => val v: t = ??? : ${CARET}Type }")

  // Wildcard bounds

  def testWildcardLowerBound1(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Invariant[_ >: Type]) match { case _: Invariant[t] => val v: Type = ??? : ${CARET}t }")

  def testWildcardLowerBound2(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : Invariant[_ >: Type]) match { case _: Invariant[t] => val v: t = ??? : Type }")

  def testWildcardUpperBound1(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Invariant[_ <: Type]) match { case _: Invariant[t] => val v: Type = ??? : t }")

  def testWildcardUpperBound2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Invariant[_ <: Type]) match { case _: Invariant[t] => val v: t = ??? : ${CARET}Type }")


  def testCovarianceWildcardLowerBound1(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Covariant[_ >: Type]) match { case _: Covariant[t] => val v: Type = ??? : ${CARET}t }")

  def testCovarianceWildcardLowerBound2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Covariant[_ >: Type]) match { case _: Covariant[t] => val v: t = ??? : ${CARET}Type }")

  def testCovarianceWildcardUpperBound1(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Covariant[_ <: Type]) match { case _: Covariant[t] => val v: Type = ??? : t }")

  def testCovarianceWildcardUpperBound2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Covariant[_ <: Type]) match { case _: Covariant[t] => val v: t = ??? : ${CARET}Type }")


  def testContravarianceWildcardLowerBound1(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Contravariant[_ >: Type]) match { case _: Contravariant[t] => val v: Type = ??? : ${CARET}t }")

  def testContravarianceWildcardLowerBound2(): Unit = checkTextHasNoErrors(Prefix +
    s"(??? : Contravariant[_ >: Type]) match { case _: Contravariant[t] => val v: t = ??? : Type }")

  def testContravarianceWildcardUpperBound1(): Unit = checkHasErrorAroundCaret(Prefix +
    "(??? : Contravariant[_ <: Type]) match { case _: Contravariant[t] => val v: Type = ??? : ${CARET}t }")

  def testContravarianceWildcardUpperBound2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Contravariant[_ <: Type]) match { case _: Contravariant[t] => val v: t = ??? : ${CARET}Type }")

  // Multiple parameters

  private val Invariant2 = "class Invariant2[A, B]; "

  def testMultiple1(): Unit = checkTextHasNoErrors(Prefix + Invariant2  +
    "(??? : Invariant2[Unit, Type]) match { case _: Invariant2[t1, t2] => val v: Type = ??? : t2 }")

  def testMultiple2(): Unit = checkTextHasNoErrors(Prefix + Invariant2  +
    s"(??? : Invariant2[Unit, Type]) match { case _: Invariant2[t1, t2] => val v: t2 = ??? : Type }")

  def testWildcard1(): Unit = checkTextHasNoErrors(Prefix + Invariant2  +
    "(??? : Invariant2[Unit, Type]) match { case _: Invariant2[_, t] => val v: Type = ??? : t }")

  def testWildcard2(): Unit = checkTextHasNoErrors(Prefix + Invariant2  +
    s"(??? : Invariant2[Unit, Type]) match { case _: Invariant2[_, t] => val v: t = ??? : Type }")

  // Parameter / argument mismatch

  def testMoreArguments(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Invariant[Type]) match { case _: Invariant[_, t] => val v: Type = ??? : ${CARET}t }")

  def testLessArguments(): Unit = checkHasErrorAroundCaret(Prefix + Invariant2 +
    s"(??? : Invariant2[Type, Unit]) match { case _: Invariant2[t$CARET] => val v: Type = ??? : t }")

  // Non-parameterized types

  def testAny1(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Any) match { case _: InvariantUpperBound[t] => val v: Type = ??? : t }")

  def testAny2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Any) match { case _: InvariantUpperBound[t] => val v: t = ??? : ${CARET}Type }")

  def testNothing1(): Unit = checkTextHasNoErrors(Prefix +
    "(??? : Nothing) match { case _: InvariantUpperBound[t] => val v: Type = ??? : t }")

  def testNothing2(): Unit = checkHasErrorAroundCaret(Prefix +
    s"(??? : Nothing) match { case _: InvariantUpperBound[t] => val v: t = ??? : ${CARET}Type }")

  // Subtyping

  def testNotSupertype(): Unit = checkHasErrorAroundCaret(Prefix + "class NotInvariantSubtype[A]; " +
    s"(??? : NotInvariantSubtype[Type]) match { case _: Invariant[t] => val v: Type = ??? : ${CARET}t }")

  def testSupertype(): Unit = checkTextHasNoErrors(Prefix + "class InvariantSubtype[A] extends Invariant[A]; " +
    s"(??? : InvariantSubtype[Type]) match { case _: Invariant[t] => val v: Type = ??? : t }")


  def testSubtype(): Unit = checkTextHasNoErrors(Prefix + "class InvariantSubtype[A] extends Invariant[A]; " +
    s"(??? : Invariant[Type]) match { case _: InvariantSubtype[t] => val v: Type = ??? : t }")

  // TODO:
  // Type aliases
  // Bivariant type parameters
  // Default type arguments in Scala 3
  // Nested patterns (now that SCL-20494 is fixed)
  // Test multiple bonds simultaneously
  // Test lower & upper bounds explicitly?
  // Pattern definition (val)
  // Type pattern (in a match type)
  // As constraints in conformance?
  // Should compare whole types rather than designators, but there are bugs in conformance
}

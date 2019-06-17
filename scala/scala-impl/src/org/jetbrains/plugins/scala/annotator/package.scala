package org.jetbrains.plugins.scala

package object annotator {

  private[annotator] sealed abstract class IntegerKind(val radix: Int,
                                                       protected val prefix: String,
                                                       val divider: Int = 2) {

    final def apply(text: String,
                    isLong: Boolean): String = text.substring(
      prefix.length,
      text.length - (if (isLong) 1 else 0)
    )

    final def get: this.type = this

    final def isEmpty: Boolean = false

    final def _1: Int = radix

    final def _2: Int = divider

    final def to(kind: IntegerKind)
                (text: String,
                 isLong: Boolean): String =
      prefix + math.BigInt(
        apply(text, isLong),
        radix
      ).toString(kind.radix)
  }

  private[annotator] object IntegerKind {

    def apply(text: String): IntegerKind = text.head match {
      case '0' if text.length > 1 =>
        text(1) match {
          case 'x' | 'X' => Hex
          case 'l' | 'L' => Dec
          case _ => Oct
        }
      case _ => Dec
    }

    def unapply(kind: IntegerKind): IntegerKind = kind
  }

  private[annotator] case object Dec extends IntegerKind(10, "", 1)

  private[annotator] case object Hex extends IntegerKind(16, "0x")

  private[annotator] case object Oct extends IntegerKind(8, "0")
}

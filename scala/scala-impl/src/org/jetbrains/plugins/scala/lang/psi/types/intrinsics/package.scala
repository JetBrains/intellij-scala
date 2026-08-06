package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStringLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.literals.{ScBooleanLiteralImpl, ScCharLiteralImpl, ScDoubleLiteralImpl, ScFloatLiteralImpl, ScIntegerLiteralImpl, ScLongLiteralImpl}

package object intrinsics {
  private[intrinsics] object AnyValue {
    def unapply(t: ScLiteralType): Option[Any] = Some(t.value.value)
  }

  private[intrinsics] object BooleanValue {
    def apply(v: Boolean)(implicit context: Project): ScLiteralType = ScLiteralType(ScBooleanLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Boolean] = t.value match {
      case ScBooleanLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private[intrinsics] object IntValue {
    def apply(v: Int)(implicit context: Project): ScLiteralType = ScLiteralType(ScIntegerLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Int] = t.value match {
      case ScIntegerLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private[intrinsics] object StringValue {
    def apply(v: String)(implicit context: Project): ScLiteralType = ScLiteralType(ScStringLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[String] = t.value match {
      case ScStringLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private[intrinsics] object CharValue {
    def apply(v: Char)(implicit context: Project): ScLiteralType = ScLiteralType(ScCharLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Char] = t.value match {
      case ScCharLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private[intrinsics] object LongValue {
    def apply(v: Long)(implicit context: Project): ScLiteralType = ScLiteralType(ScLongLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Long] = t.value match {
      case ScLongLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private[intrinsics] object FloatValue {
    def apply(v: Float)(implicit context: Project): ScLiteralType = ScLiteralType(ScFloatLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Float] = t.value match {
      case ScFloatLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }

  private[intrinsics] object DoubleValue {
    def apply(v: Double)(implicit context: Project): ScLiteralType = ScLiteralType(ScDoubleLiteralImpl.Value(v))

    def unapply(t: ScLiteralType): Option[Double] = t.value match {
      case ScDoubleLiteralImpl.Value(v) => Some(v)
      case _ => None
    }
  }
}

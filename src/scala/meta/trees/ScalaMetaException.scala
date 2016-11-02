package scala.meta.trees

import com.intellij.psi.PsiElement

import scala.meta.internal.{ast => m, semantic => h}

class AbortException(reason: String) extends RuntimeException(reason) {
  def this(place: Any, mess: String) = this(mess + s"[$place]")
}

class UnimplementedException(what: Any) extends
  AbortException(what, s"This code path is not implemented yet[${Thread.currentThread().getStackTrace.drop(3).head}]")

class ScalaMetaException(message: String) extends Exception(message)

class ScalaMetaResolveError(elem: PsiElement) extends ScalaMetaException(s"Cannot resolve ${elem.getClass} at ${elem.toString}")

class ScalaMetaTypeResultFailure(elem: Option[PsiElement], cause: String) extends ScalaMetaException(s"Cannot calculate type at ${elem.map(_.getText).getOrElse("UNKNOWN")}($cause)")

package object error {
  def unreachable = throw new AbortException("This code should be unreachable")
  def unreachable(reason: String) = throw new AbortException("This code should be unreachable: " + reason)
  def unresolved(cause: String, place: Option[PsiElement]) = throw new AbortException(place, s"""Failed to typecheck "${place.map(_.getText).getOrElse("UNKNOWN")}" - $cause""")
  def die(reason: String = "unknown") = throw new AbortException(reason)
}

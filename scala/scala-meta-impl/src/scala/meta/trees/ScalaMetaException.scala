package scala.meta.trees

import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nls

import scala.meta.ScalaMetaBundle

class AbortException(@Nls reason: String) extends RuntimeException(reason) {
  def this(place: Any, @Nls mess: String) = this(mess + s"[$place]")
}

class UnimplementedException(what: Any) extends
  AbortException(what, ScalaMetaBundle.message("this.code.path.is.not.implemented.yet.head", Thread.currentThread().getStackTrace.drop(3).head))

class ScalaMetaException(@Nls message: String) extends Exception(message)

class ScalaMetaResolveError(elem: PsiElement) extends ScalaMetaException(ScalaMetaBundle.message("cannot.resolve.class.at.element", elem.getClass, elem.toString))

class ScalaMetaTypeResultFailure(@Nls cause: String) extends ScalaMetaException(ScalaMetaBundle.message("cannot.calculate.type", cause))

package object error {
  def unreachable = throw new AbortException(ScalaMetaBundle.message("this.code.should.be.unreachable"))
  def unreachable(@Nls reason: String) = throw new AbortException(ScalaMetaBundle.message("this.code.should.be.unreachable.reason", reason))
  def unresolved(@Nls cause: String) = throw new AbortException(ScalaMetaBundle.message("failed.to.typecheck", cause))
  def die(@Nls reason: String = "unknown") = throw new AbortException(reason)
}

package org.jetbrains.plugins.scala.lang.scaladoc.psi.impl

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.resolve.StdKinds._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, ScalaResolveResult}
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference

class ScDocResolvableCodeReferenceImpl(node: ASTNode) extends ScStableCodeReferenceImpl(node) with ScDocResolvableCodeReference {

  override protected def debugKind: Option[String] = Some("scalaDoc")

  override def multiResolveScala(incomplete: Boolean): Array[ScalaResolveResult] =
    super.multiResolveScala(incomplete).map {
      case ScalaResolveResult(cstr: ScPrimaryConstructor, _) if cstr.containingClass != null =>
        new ScalaResolveResult(cstr.containingClass)
      case result => result
    }

  override def getKinds(incomplete: Boolean, completion: Boolean): ResolveTargets.ValueSet =
    stableImportSelector
}
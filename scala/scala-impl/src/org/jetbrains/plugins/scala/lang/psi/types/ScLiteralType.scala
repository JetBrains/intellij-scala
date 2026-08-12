package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.project.ProjectContext

final class ScLiteralType private(val value: ScLiteral.Value[_],
                                  val allowWiden: Boolean,
                                  // The psiElement, this literal type was created from
                                  // Especially useful in Scala3,
                                  // where String literals are used more and more
                                  // to reference real definitions.
                                  val psiElement: Option[PsiElement])
                                 (implicit project: Project)
  extends api.ValueType with LeafType {

  override implicit def projectContext: ProjectContext = project

  override def visitType(visitor: ScalaTypeVisitor): Unit = visitor.visitLiteralType(this)

  def wideType: ScType = value.wideType

  def blockWiden: ScLiteralType = if (allowWiden) new ScLiteralType(value, allowWiden = false, psiElement = psiElement) else this

  override def equals(obj: Any): Boolean = obj match {
    case other: ScLiteralType => value == other.value
    case _                    => false
  }

  override def hashCode: Int = value.hashCode
}

object ScLiteralType {

  import ScLiteral.Value

  def apply(value: Value[_],
            allowWiden: Boolean = true,
            @Nullable psiElement: PsiElement = null)
           (implicit project: Project) =
    new ScLiteralType(value, allowWiden, Option(psiElement))

  def unapply(literalType: ScLiteralType): Some[(Value[_], Boolean)] =
    Some(literalType.value, literalType.allowWiden)
}

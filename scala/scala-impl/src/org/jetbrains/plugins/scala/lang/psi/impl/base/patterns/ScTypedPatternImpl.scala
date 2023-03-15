package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTypedPatternImpl private(stub: ScBindingPatternStub[ScTypedPattern], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.TYPED_PATTERN, node)
    with ScPatternImpl
    with ScTypedPattern
    with TypedPatternLikeImpl
{
  def this(node: ASTNode) = this(null, node)

  def this(stub: ScBindingPatternStub[ScTypedPattern]) = this(stub, null)

  override def nameId: PsiElement = findChildByType[PsiElement](TokenSets.ID_SET)

  override def typePattern: Option[ScTypePattern] = findChild[ScTypePattern]

  override def toString: String = "TypedPattern: " + ifReadAllowed(name)("")

  override def `type`(): TypeResult =
    typePattern match {
      case Some(tp) =>
        //TODO: aliases, wildcards
        if (tp.typeElement == null) return Failure(ScalaBundle.message("no.type.element.for.typed.pattern"))

        val typeElementType = tp.typeElement.`type`()

        this.expectedType match {
          case Some(expectedType) => typeElementType.map(expectedType.glb(_))
          case _                  => typeElementType
        }
      case None => Failure(ScalaBundle.message("no.type.pattern"))
    }

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean =
    ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, `type`())

  override def getOriginalElement: PsiElement = super[ScTypedPattern].getOriginalElement
}

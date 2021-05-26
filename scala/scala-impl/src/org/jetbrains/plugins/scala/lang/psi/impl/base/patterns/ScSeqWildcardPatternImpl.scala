package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.{PsiElement, ResolveState}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

class ScSeqWildcardPatternImpl private(stub: ScBindingPatternStub[ScSeqWildcardPattern], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.SEQ_WILDCARD_PATTERN, node) with ScPatternImpl with ScSeqWildcardPattern {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScBindingPatternStub[ScSeqWildcardPattern]) = this(stub, null)

  // The SeqWildCard pattern is never irrefutable, with the exception that it occurs
  // in the exact position of an Constructor pattern.
  // See
  //   ScConstructorPattern.extractsRepeatedParameterIrrefutably
  override def isIrrefutableFor(t: Option[ScType]): Boolean = false

  override def nameId: PsiElement = findChildByType[PsiElement](TokenSets.ID_SET)

  override def isWildcard: Boolean = findChildByType[PsiElement](ScalaTokenTypes.tUNDER) != null

  override def toString: String = "SequenceWildcardPattern: " + ifReadAllowed(name)("")


  override def `type`(): TypeResult =
    this.expectedType match {
      case Some(x) => Right(x)
      case _ =>  Failure(ScalaBundle.message("no.expected.type.for.wildcard.naming"))
    }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (isStable) {
      ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, `type`())
    } else true
  }

  override def getOriginalElement: PsiElement = super[ScSeqWildcardPattern].getOriginalElement
}
package org.jetbrains.plugins.scala.lang.psi.impl.statements


import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.types.{Any, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}

/**
 * @author Jason Zaugg
 */
class ScMacroDefinitionImpl extends ScFunctionImpl with ScMacroDefinition {
  def this(node: ASTNode) = {
    this(); setNode(node)
  }

  def this(stub: ScFunctionStub) = {
    this(); setStub(stub); setNode(null)
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    //process function's parameters for dependent method types, and process type parameters
    if (!super[ScFunctionImpl].processDeclarations(processor, state, lastParent, place)) return false

    //do not process parameters for default parameters, only for function body
    //processing parameters for default parameters in ScParameters
    val parameterIncludingSynthetic: Seq[ScParameter] = effectiveParameterClauses.flatMap(_.parameters)
    if (getStub == null) {
      body match {
        case Some(x)
          if lastParent != null &&
            (!needCheckProcessingDeclarationsForBody ||
            x.getStartOffsetInParent == lastParent.getStartOffsetInParent) =>
          for (p <- parameterIncludingSynthetic) {
            ProgressManager.checkCanceled()
            if (!processor.execute(p, state)) return false
          }
        case _ =>
      }
    } else {
      if (lastParent != null && lastParent.getContext != lastParent.getParent) {
        for (p <- parameterIncludingSynthetic) {
          ProgressManager.checkCanceled()
          if (!processor.execute(p, state)) return false
        }
      }
    }
    true
  }

  protected def needCheckProcessingDeclarationsForBody = true

  override def toString: String = "ScMacroDefinition: " + name

  def returnTypeInner: TypeResult[ScType] = returnTypeElement match {
    case None => Success(doGetType(), Some(this)) // TODO look up type from the macro impl.
    case Some(rte: ScTypeElement) => rte.getType(TypingContext.empty)
  }

  def body: Option[ScExpression] = {
    val stub = getStub
    if (stub != null) stub.asInstanceOf[ScFunctionStub].getBodyExpression else findChild(classOf[ScExpression])
  }

  override def hasAssign: Boolean = true

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitMacroDefinition(this)
  }

  override def getType(ctx: TypingContext): TypeResult[ScType] = {
    super.getType(ctx)
  }

  def doGetType() = {
    name match {
      case "doMacro" =>
        ScalaPsiElementFactory.createTypeElementFromText("(Int, String)", getManager).getType().get
      case _ => Any
    }
  }
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitMacroDefinition(this)
      case _ => super.accept(visitor)
    }
  }
}

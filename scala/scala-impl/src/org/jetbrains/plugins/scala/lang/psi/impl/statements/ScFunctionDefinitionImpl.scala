package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.{ScFunctionDefinitionFactory, ScFunctionFactory}
import org.jetbrains.plugins.scala.extensions.{StubBasedExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.FUNCTION_DEFINITION
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl.{importantOrderFunction, isCalculatingFor, returnTypeInner}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, api}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */

class ScFunctionDefinitionImpl(stub: ScFunctionStub[ScFunctionDefinition],
                               nodeType: ScFunctionElementType[ScFunctionDefinition],
                               node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScFunctionDefinition {

  override protected def shouldProcessParameters(lastParent: PsiElement): Boolean =
    super.shouldProcessParameters(lastParent) || body.contains(lastParent)

  override def toString: String = "ScFunctionDefinition: " + ifReadAllowed(name)("")

  //types of implicit definitions without explicit type should be computed in the right order
  override def returnType: TypeResult = {
    if (importantOrderFunction(this)) {
      val parent = getParent
      val isCalculating = isCalculatingFor(parent)

      if (isCalculating.get()) returnTypeInner(this)
      else {
        isCalculating.set(true)
        try {
          val children = parent.stubOrPsiChildren(FUNCTION_DEFINITION, ScFunctionDefinitionFactory).iterator

          while (children.hasNext) {
            val nextFun = children.next()
            if (importantOrderFunction(nextFun)) {
              ProgressManager.checkCanceled()
              val nextReturnType = returnTypeInner(nextFun)

              //stop at current function to avoid recursion
              //if we are currently computing type in some implicit function body below
              if (nextFun == this) {
                return nextReturnType
              }
            }
          }
          returnTypeInner(this)
        }
        finally {
          isCalculating.set(false)
        }
      }
    } else returnTypeInner(this)
  }

  override def body: Option[ScExpression] = byPsiOrStub(findChild[ScExpression])(_.bodyExpression)

  override def hasAssign: Boolean = byStubOrPsi(_.hasAssign)(assignment.isDefined)

  override def getBody: FakePsiCodeBlock = body match {
    case Some(b) => new FakePsiCodeBlock(b) // Needed so that LineBreakpoint.canAddLineBreakpoint allows line breakpoints on one-line method definitions
    case None    => null
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitFunctionDefinition(this)
}

private object ScFunctionDefinitionImpl {
  import org.jetbrains.plugins.scala.project.UserDataHolderExt

  private val calculatingBlockKey: Key[ThreadLocal[Boolean]] = Key.create("calculating.function.returns.block")

  private def isCalculatingFor(e: PsiElement) = e.getOrUpdateUserData(
    calculatingBlockKey,
    ThreadLocal.withInitial[Boolean](() => false)
  )

  private def importantOrderFunction(function: ScFunction): Boolean = function match {
    case funDef: ScFunctionDefinition => funDef.hasModifierProperty("implicit") && !funDef.hasExplicitType
    case _ => false
  }

  private def returnTypeInner(fun: ScFunctionDefinition): TypeResult = {
    import fun.projectContext

    fun.returnTypeElement match {
      case None if !fun.hasAssign => Right(api.Unit)
      case None =>
        fun.body match {
          case Some(b) => b.`type`().map(ScLiteralType.widenRecursive)
          case _       => Right(api.Unit)
        }
      case Some(rte: ScTypeElement) => rte.`type`()
    }
  }

}
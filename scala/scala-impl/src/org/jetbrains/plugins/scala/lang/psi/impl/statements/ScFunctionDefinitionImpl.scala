package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.{Key, ModificationTracker}
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.{CachedValue, CachedValueProvider, CachedValuesManager}
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScFunctionDefinitionFactory
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cached}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt, StubBasedExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.FUNCTION_DEFINITION
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause.{TermClause, TypeClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScNamedBeginImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionDefinitionImpl.{importantOrderFunction, isCalculatingFor, returnTypeInner, returnUsagesKey}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, canNotBeOverridden}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType.{ImplicitValueClass, ImplicitValueClassDumbMode}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{Context, Widening, api}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.UnloadableThreadLocal

class ScFunctionDefinitionImpl[S <: ScFunctionDefinition](stub: ScFunctionStub[S],
                                                          nodeType: ScFunctionElementType[S],
                                                          node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScFunctionDefinition
    with ScNamedBeginImpl {

  override def getContainingClass: PsiClass =
    super.getContainingClass match {
      case containingClazz@ImplicitValueClass(c) if !this.isPrivate && !this.isProtected =>
        c.fakeCompanionModule.getOrElse(containingClazz)
      case containingClazz => containingClazz
    }

  /**
   * Note that this method is only called in non-Scala contexts. For Scala contexts super#name is used.
   *
   * The below represents the special case for public function definitions of implicit classes that extend AnyVal -- a
   * common approach for Scala 2 extension methods. Such functions, from the perspective of non-Scala JVM languages,
   * have `$extension` appended to their name. See https://docs.scala-lang.org/overviews/core/value-classes.html#extension-methods.
   */
  override def getName: String =
    if (this.isPrivate || this.isProtected) {
      super.getName
    } else {
      containingClass match {
        case ImplicitValueClassDumbMode(_) => super.getName + "$extension"
        case _ => super.getName
      }
    }

  override protected def shouldProcessParameters(lastParent: PsiElement): Boolean =
    super.shouldProcessParameters(lastParent) || body.contains(lastParent)

  override def toString: String = "ScFunctionDefinition: " + ifReadAllowed(name)("")

  //types of implicit definitions without explicit type should be computed in the right order
  override def returnType: TypeResult = {
    if (importantOrderFunction(this)) {
      val parent = getParent
      val isCalculating = isCalculatingFor(parent)

      if (isCalculating.value) returnTypeInner(this)
      else {
        isCalculating.value = true
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
          isCalculating.value = false
        }
      }
    } else returnTypeInner(this)
  }

  override def body: Option[ScExpression] = byPsiOrStub(findChild[ScExpression])(_.bodyExpression)

  override def returnUsages: Set[ScExpression] = byPsiOrStub {
    // subtreeChanged invalidates this cache even for edits inside nested stable-type blocks.
    CachedValuesManager.getCachedValue(this, returnUsagesKey, () =>
      CachedValueProvider.Result.create(calculateReturnUsages, ModificationTracker.NEVER_CHANGED)
    )
  } { _ =>
    // Stub bodies are detached PSI; their changes do not pass through this function's subtreeChanged.
    calculateReturnUsages
  }

  private def calculateReturnUsages: Set[ScExpression] = {
    val bodyExpression = body match {
      case Some(value) => value
      case None => return Set.empty
    }

    val allReturnStatements: Set[ScReturn] = {
      bodyExpression.depthFirst(!_.is[ScFunction])
        .collect { case statement: ScReturn => statement }
        .toSet
    }

    val tailExpressions: Set[ScExpression] = bodyExpression.calculateTailReturns
    val file = bodyExpression.getContainingFile
    (allReturnStatements ++ tailExpressions).filter(_.getContainingFile == file)
  }

  override def subtreeChanged(): Unit = {
    super.subtreeChanged()
    putUserData(returnUsagesKey, null)
  }

  override def hasAssign: Boolean = byStubOrPsi(_.hasAssign)(assignment.isDefined)

  override def getBody: FakePsiCodeBlock = body match {
    case Some(b) => new FakePsiCodeBlock(b) // Needed so that LineBreakpoint.canAddLineBreakpoint allows line breakpoints on one-line method definitions
    case None    => null
  }

  override private[psi] def superMethodCall: ScExpression = _superMethodCall()

  private val _superMethodCall = cached("superMethodCall", BlockModificationTracker(this), () => {
    val signatureArguments = signatureClauses.map {
      case TypeClause(clause) =>
        clause.typeParameters.map(_.name).mkString("[", ", ", "]")
      case TermClause(clause) =>
        if (clause.parameters.isEmpty) "()"
        else clause.parameters.map(_.name).mkString(if (clause.hasUsingKeyword) "(using " else "(", ", ", ")")
    }.mkString

    val text = "super." + name + signatureArguments

    val call = ScalaPsiElementFactory.createExpressionFromText(text, this.features)
    call.context = body.getOrElse(this)
    call
  })

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitFunctionDefinition(this)

  override protected def keywordTokenType: IElementType = ScalaTokenTypes.kDEF

  override def namedTag: Option[ScNamedElement] = declaredElements.headOption

  override def isEffectivelyFinal: Boolean = canNotBeOverridden(this)
}

private object ScFunctionDefinitionImpl {
  import org.jetbrains.plugins.scala.project.UserDataHolderExt

  // Non-copyable user data keeps cached expressions out of copies of the function.
  private val returnUsagesKey: Key[CachedValue[Set[ScExpression]]] = Key.create("scala.function.return.usages")

  private val calculatingBlockKey: Key[UnloadableThreadLocal[Boolean]] = Key.create("calculating.function.returns.block")

  private def isCalculatingFor(e: PsiElement): UnloadableThreadLocal[Boolean] = e.getOrUpdateUserData(
    calculatingBlockKey,
    new UnloadableThreadLocal(false)
  )

  private def importantOrderFunction(function: ScFunction): Boolean = function match {
    case funDef: ScFunctionDefinition => funDef.hasModifierProperty("implicit") && !funDef.hasExplicitType
    case _ => false
  }

  private def returnTypeInner(fun: ScFunctionDefinition): TypeResult = {
    import fun.projectContext
    implicit val context: Context = Context(fun)

    fun.returnTypeElement match {
      case None if fun.isConstructor => fun.containingClass.`type`()
      case None if !fun.hasAssign => Right(api.Unit)
      case None =>
        fun.body match {
          case Some(b) =>
            def rhsType = b.`type`().map(Widening.widenInferredDefinitionType(_, Widening.DefinitionKind.Def))
            if (fun.scalaLanguageLevel.exists(_.isScala3) && !fun.isExtensionMethod) fun.superMethod match {
              case Some(f: ScFunction) if f.getTypeParameters.length == fun.getTypeParameters.length =>
                val superMethod = fun.superMethodCall
                superMethod.`type`()
              case _ => rhsType
            }
            else rhsType
          case _ => Right(api.Unit)
        }
      case Some(rte: ScTypeElement) => rte.`type`()
    }
  }
}

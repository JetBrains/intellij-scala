package org.jetbrains.plugins.scala.structuralSearch

import com.intellij.dupLocator.util.DuplocatorUtil
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.text.Strings
import com.intellij.psi.PsiElement
import com.intellij.structuralsearch.impl.matcher.MatchContext
import com.intellij.structuralsearch.impl.matcher.compiler.GlobalCompilingVisitor.OccurenceKind
import com.intellij.structuralsearch.impl.matcher.compiler.{GlobalCompilingVisitor, WordOptimizer}
import com.intellij.structuralsearch.impl.matcher.handlers.{MatchingHandler, SubstitutionHandler, TopLevelMatchingHandler}
import com.intellij.structuralsearch.impl.matcher.strategies.MatchingStrategy
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScCaseClause, ScReferencePattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScNamedTupleTypeComponent, ScTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScConstructorInvocation, ScReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{MethodInvocation, ScForBinding, ScGuard, ScInfixExpr, ScMethodCall, ScNamedTupleExprComponent}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScPatternDefinition, ScTypeAlias, ScValueDeclaration, ScVariableDeclaration, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGiven, ScGivenAlias, ScGivenDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaPsiElement, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.structuralSearch.filter.{AcceptAllFilter, CaseClauseFilter, FunctionFilter, GivenFilter, LeafIdentifierFilter, MethodInvocationFilter, TypeAliasFilter, TypeDefinitionFilter, TypeElementFilter, TypeParamFilter, ValVarFilter}
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

class ScalaCompilingVisitor(globalVisitor: GlobalCompilingVisitor) extends ScalaRecursiveElementVisitor {

  def compile(topLevelElements: Array[PsiElement]): Unit = {
    globalVisitor.getContext.getPattern.setStrategy(new MatchingStrategy() {
      // Determine if we should match on this node
      // --> use it to only select nodes of the correct language
      override def continueMatching(start: PsiElement): Boolean =
        start.getLanguage match {
          case ScalaLanguage.INSTANCE | Scala3Language.INSTANCE => true
          case _ => false
        }

      override def shouldSkip(element: PsiElement, elementToMatchWith: PsiElement): Boolean =
        DuplocatorUtil.shouldSkip(element, elementToMatchWith)
    })

    val context = globalVisitor.getContext
    val optimizer = Option.when(!DumbService.isDumb(context.getProject))(new ScalaWordOptimizer())
    val pattern = context.getPattern
    for (element <- topLevelElements) {
      element.accept(this)
      optimizer.foreach(element.accept)
      pattern.setHandler(element, new TopLevelMatchingHandler(pattern.getHandler(element)))
    }
  }

  override def visitReference(ref: ScReference): Unit = {
    super.visitReference(ref)
    placeVarHandler(ref.refName)
    globalVisitor.getContext.getPattern
      .getHandler(ref).setFilter(new AcceptAllFilter())
  }

  override def visitTypeAlias(alias: ScTypeAlias): Unit = {
    super.visitTypeAlias(alias)
    placeVarHandler(alias.name)
    globalVisitor.getContext.getPattern
      .getHandler(alias).setFilter(new TypeAliasFilter())
  }

  private class SymbolHandler(handler: SubstitutionHandler) extends MatchingHandler {
    override def `match`(patternNode: PsiElement, matchedNode: PsiElement, context: MatchContext): Boolean =
      handler.handle(matchedNode, context)
  }

  override def visitInfixExpression(infix: ScInfixExpr): Unit = visitMethodInvocation(infix)
  override def visitMethodCallExpression(call: ScMethodCall): Unit = visitMethodInvocation(call)

  def visitMethodInvocation(call: MethodInvocation): Unit = {
    super.visitExpression(call)

    globalVisitor
      .getContext.getPattern
      .getHandler(call).setFilter(new MethodInvocationFilter())
  }

  override def visitScalaElement(element: ScalaPsiElement): Unit = {
    globalVisitor.handle(element)
    element match {
      case _: ScReferencePattern => placeVarHandler(element.getText)
      case typeParam: ScTypeParam => visitTypeParam(typeParam)
      case _ =>
    }
    super.visitScalaElement(element)
  }

  override def visitParameter(parameter: ScParameter): Unit = {
    super.visitParameter(parameter)

    placeVarHandler(parameter.name)
  }

  override def visitCaseClause(cc: ScCaseClause): Unit = {
    super.visitCaseClause(cc)

    cc.pattern match {
      case Some(refPat: ScReferencePattern) =>
        placeVarHandler(refPat.getText)
      case _ =>
    }
    globalVisitor
      .getContext.getPattern
      .getHandler(cc).setFilter(new CaseClauseFilter())
  }

  override def visitFunction(fun: ScFunction): Unit = {
    super.visitFunction(fun)

    placeVarHandler(fun.name)
    globalVisitor
      .getContext.getPattern
      .getHandler(fun).setFilter(new FunctionFilter())
  }

  override def visitGiven(g: ScGiven): Unit = {
    super.visitScalaElement(g)
    val pattern = globalVisitor.getContext.getPattern

    for (nameElement <- g.nameElement) {
      placeVarHandler(nameElement.getText)
      globalVisitor.handle(nameElement)
      pattern.getHandler(nameElement).setFilter(new LeafIdentifierFilter)
    }

    pattern.getHandler(g).setFilter(new GivenFilter())
  }

  override def visitTypeElement(te: ScTypeElement): Unit = {
    super.visitTypeElement(te)
    placeVarHandler(te.getFirstChild.getText)
    globalVisitor
      .getContext.getPattern
      .getHandler(te).setFilter(new TypeElementFilter())
  }

  private def visitTypeParam(typeParam: ScTypeParam): Unit = {
    placeVarHandler(typeParam.getText)
    globalVisitor
      .getContext.getPattern
      .getHandler(typeParam).setFilter(new TypeParamFilter())
  }

  def visitNamedTupleExprComponent(namedTupleComp: ScNamedTupleExprComponent): Unit = {
    placeVarHandler(namedTupleComp.name, false)
  }

  def visitNamedTupleTypeComponent(namedTupleComp: ScNamedTupleTypeComponent): Unit = {
    placeVarHandler(namedTupleComp.name, false)
  }

  override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
    super.visitTypeDefinition(typedef)
    placeVarHandler(typedef.name)
    globalVisitor
      .getContext.getPattern
      .getHandler(typedef).setFilter(new TypeDefinitionFilter())
  }

  override def visitForBinding(forBinding: ScForBinding): Unit = {
    super.visitForBinding(forBinding)
    placeVarHandler(forBinding.getText)
  }

  override def visitAnnotation(annotation: ScAnnotation): Unit = {
    super.visitAnnotation(annotation)
    placeVarHandler(annotation.constructorInvocation.typeElement.getText)
  }

  override def visitGuard(guard: ScGuard): Unit = {
    super.visitGuard(guard)
    placeVarHandler(guard.expr.map(_.getText).getOrElse(""), false)
  }

  override def visitConstructorInvocation(constrInvocation: ScConstructorInvocation): Unit = {
    super.visitConstructorInvocation(constrInvocation)
    placeVarHandler(constrInvocation.typeElement.getText)
  }

  override def visitPatternDefinition(pat: ScPatternDefinition): Unit = {
    super.visitPatternDefinition(pat)
    if (pat.declaredNames.size == 1)
      placeVarHandler(pat.declaredNames.head)
    globalVisitor
      .getContext.getPattern
      .getHandler(pat).setFilter(new ValVarFilter())
  }

  override def visitValueDeclaration(v: ScValueDeclaration): Unit = {
    super.visitValueDeclaration(v)
    if (v.declaredNames.size == 1)
      placeVarHandler(v.declaredNames.head)
    globalVisitor
      .getContext.getPattern
      .getHandler(v).setFilter(new ValVarFilter())
  }

  override def visitVariableDefinition(varr: ScVariableDefinition): Unit = {
    super.visitVariableDefinition(varr)
    if (varr.declaredNames.size == 1)
      placeVarHandler(varr.declaredNames.head)
    globalVisitor
      .getContext.getPattern
      .getHandler(varr).setFilter(new ValVarFilter())
  }

  override def visitVariableDeclaration(varr: ScVariableDeclaration): Unit = {
    super.visitVariableDeclaration(varr)
    if (varr.declaredNames.size == 1)
      placeVarHandler(varr.declaredNames.head)
    globalVisitor
      .getContext.getPattern
      .getHandler(varr).setFilter(new ValVarFilter())
  }

  override def visitImportExpr(expr: ScImportExpr): Unit = {
    super.visitImportExpr(expr)
  }

  private def placeVarHandler(name: String, setFilter: Boolean = true): Unit = {
    val pattern = globalVisitor.getContext.getPattern
    if (pattern.isTypedVar(name)) {
      pattern.getHandler(name) match {
        case substHand: SubstitutionHandler =>
          if (setFilter)
            substHand.setFilter(new AcceptAllFilter())
          substHand.setMatchHandler(new MatchingHandler {
            override def `match`(patternNode: PsiElement, matchedNode: PsiElement, context: MatchContext): Boolean = {
              matchedNode.accept(ScalaCompilingVisitor.this)
              context.getMatcher.`match`(patternNode, matchedNode)
            }
          })
        case _ =>
      }
    }
  }

  class ScalaWordOptimizer extends ScalaRecursiveElementVisitor, WordOptimizer {

    override def visitReference(ref: ScReference): Unit = {
      val word = ref.refName
      if (ref.resolve == null && Strings.isCapitalized(word)) return
      if (!handleWord(word, OccurenceKind.CODE, globalVisitor.getContext)) {
        super.visitReference(ref)
      }
    }

    override def visitGivenAlias(g: ScGivenAlias): Unit = {
      if (g.nameElement.isDefined) {
        this.visitFunction(g)
      } else {
        super.visitFunction(g)
      }
    }

    override def visitGivenDefinition(g: ScGivenDefinition): Unit = {
      if (g.nameElement.isDefined) {
        this.visitTypeDefinition(g)
      } else {
        super.visitTypeDefinition(g)
      }
    }

    override def visitFunction(fun: ScFunction): Unit = {
      if (!handleWord(fun.name, OccurenceKind.CODE, globalVisitor.getContext)) return
      super.visitFunction(fun)
    }

    override def visitTypeDefinition(typedef: ScTypeDefinition): Unit = {
      if (!handleWord(typedef.name, OccurenceKind.CODE, globalVisitor.getContext)) return
      super.visitTypeDefinition(typedef)
    }
  }
}

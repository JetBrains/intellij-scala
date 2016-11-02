package scala.meta.trees

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.types.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, api => p, types => ptype}

import scala.collection.immutable.Seq
import scala.language.postfixOps
//import scala.meta.internal.ast.Term
//import scala.meta.internal.ast.Term.Param
import scala.meta.internal.{semantic => h}
import scala.{meta=>m}
import scala.meta.trees.error._
import scala.{Seq => _}

trait TreeAdapter {
  self: TreeConverter =>

  def ideaToMeta(tree: PsiElement): m.Tree = {
    ProgressManager.checkCanceled()
    tree match {
      case t: ScValueDeclaration => toVal(t)
      case t: ScVariableDeclaration => toVar(t)
      case t: ScTypeAliasDeclaration => toTypeDecl(t)
      case t: ScTypeAliasDefinition => toTypeDefn(t)
      case t: ScFunctionDeclaration => toFunDecl(t)
      case t: ScPatternDefinition => toPatternDefinition(t)
      case t: ScVariableDefinition => toVarDefn(t)
      case t: ScFunctionDefinition => toFunDefn(t)
      case t: ScMacroDefinition => toMacroDefn(t)
      case t: ScPrimaryConstructor => ctor(Some(t))
      case t: ScTrait => toTrait(t)
      case t: ScClass => toClass(t)
      case t: ScObject => toObject(t)
      case t: ScExpression => expression(Some(t)).get
      case t: p.toplevel.imports.ScImportStmt => m.Import(Seq(t.importExprs.map(imports):_*))

      case t: PsiClass => toClass(t)
      case t: PsiMethod => t ???

      case other => other ?!
    }
  }

  def toMacroDefn(t: ScMacroDefinition): m.Defn.Macro = {
    if (t.definedReturnType.isEmpty)
      unreachable("Macro definition must have return type defined")
    m.Defn.Macro(
      convertMods(t), toTermName(t),
      Seq(t.typeParameters map toTypeParams: _*),
      Seq(t.paramClauses.clauses.map(convertParamClause): _*),
      t.definedReturnType.map(toType(_)).toOption,
      expression(t.body).get
    )
  }

  def toFunDefn(t: ScFunctionDefinition): m.Defn.Def = {
    m.Defn.Def(convertMods(t), toTermName(t),
      Seq(t.typeParameters map toTypeParams: _*),
      Seq(t.paramClauses.clauses.map(convertParamClause): _*),
      t.definedReturnType.map(toType(_)).toOption,
      expression(t.body).getOrElse(m.Term.Block(Nil))
    )
  }

  // Java conversion - beware: we cannot convert java method bodies, so just return a lazy ???
  def toMethodDefn(t: PsiMethod): m.Defn.Def = {
    t ???
  }

  def toVarDefn(t: ScVariableDefinition): m.Defn.Var = {
    def pattern(bp: ScBindingPattern) = m.Pat.Var.Term(toTermName(bp))
    m.Defn.Var(convertMods(t), Seq(t.bindings.map(pattern): _*), t.declaredType.map(toType(_)), expression(t.expr))
  }

  def toFunDecl(t: ScFunctionDeclaration): m.Decl.Def = {
    m.Decl.Def(convertMods(t), toTermName(t), Seq(t.typeParameters map toTypeParams: _*), Seq(t.paramClauses.clauses.map(convertParamClause): _*), returnType(t.returnType))
  }

  def toTypeDefn(t: ScTypeAliasDefinition): m.Defn.Type = {
    m.Defn.Type(convertMods(t), toTypeName(t), Seq(t.typeParameters map toTypeParams: _*), toType(t.aliasedType))
  }

  def toTypeDecl(t: ScTypeAliasDeclaration): m.Decl.Type = {
    m.Decl.Type(convertMods(t), toTypeName(t), Seq(t.typeParameters map toTypeParams: _*), typeBounds(t))
  }

  def toVar(t: ScVariableDeclaration): m.Decl.Var = {
    m.Decl.Var(convertMods(t), Seq(t.getIdList.fieldIds map { it => m.Pat.Var.Term(toTermName(it)) }: _*), toType(t.typeElement.get))
  }

  def toVal(t: ScValueDeclaration): m.Decl.Val = {
    m.Decl.Val(convertMods(t), Seq(t.getIdList.fieldIds map { it => m.Pat.Var.Term(toTermName(it)) }: _*), toType(t.typeElement.get))
  }

  def toTrait(t: ScTrait) = m.Defn.Trait(
    convertMods(t),
    toTypeName(t),
    Seq(t.typeParameters map toTypeParams:_*),
    m.Ctor.Primary(Nil, m.Ctor.Ref.Name("this"), Nil),
    template(t.extendsBlock)
  )

  def toClass(c: ScClass) = m.Defn.Class(
    convertMods(c),
    toTypeName(c),
    Seq(c.typeParameters map toTypeParams:_*),
    ctor(c.constructor),
    template(c.extendsBlock)
  )

  def toClass(c: PsiClass) = m.Defn.Class(
    convertMods(c.getModifierList),
    toTypeName(c),
    Seq(c.getTypeParameters map toTypeParams:_*),
    ctor(c),
    template(c.getAllMethods)
  )

  def toObject(o: ScObject) = m.Defn.Object(
    convertMods(o),
    toTermName(o),
    template(o.extendsBlock)
  )

  def ctor(pc: Option[ScPrimaryConstructor]): m.Ctor.Primary = {
    pc match {
      case Some(ctor) => m.Ctor.Primary(convertMods(ctor), toPrimaryCtorName(ctor), Seq(ctor.parameterList.clauses.map(convertParamClause):_*))
      case None => unreachable("no primary constructor in class")
    }
  }

  // FIXME: we don't have explicit information on what ctor has been used, so just select first one
  def ctor(c: PsiClass): m.Ctor.Primary = {
//    m.Ctor.Primary(Seq.empty, m.Ctor.Ref.Name(c.getName).withDenot())
    c ???
  }

  def caseClause(c: patterns.ScCaseClause): m.Case = {
    m.Case(pattern(c.pattern.get), c.guard.map(it => expression(it.expr.get)), expression(c.expr.get).asInstanceOf[m.Term.Block])
  }

  def pattern(pt: patterns.ScPattern): m.Pat = {
    import p.base.patterns._

    import m.Pat._
    def compose(lst: Seq[ScPattern]): m.Pat = lst match {
      case x :: Nil => pattern(x)
      case x :: xs  => Alternative(pattern(x), compose(xs))
    }
    // WHY??(((
    def arg(pt: patterns.ScPattern): m.Pat.Arg = pt match {
      case t: ScSeqWildcard       =>  Arg.SeqWildcard()
      case t: ScWildcardPattern   =>  Wildcard()
      case t: ScStableReferenceElementPattern => toTermName(t.refElement.get)
      case t: ScPattern           => pattern(t)
    }
    pt match {
      case t: ScReferencePattern  =>  Var.Term(toTermName(t))
      case t: ScConstructorPattern=>  Extract(toTermName(t.ref), Nil, Seq(t.args.patterns.map(arg):_*))
      case t: ScNamingPattern     =>  Bind(Var.Term(toTermName(t)), arg(t.named))
      case t@ ScTypedPattern(te: types.ScWildcardTypeElement) => Typed(if (t.isWildcard) Wildcard() else Var.Term(toTermName(t)), Type.Wildcard())
      case t@ ScTypedPattern(te)  =>  Typed(if (t.isWildcard) Wildcard() else Var.Term(toTermName(t)), toType(te).patTpe)
      case t: ScLiteralPattern    =>  literal(t.getLiteral)
      case t: ScTuplePattern      =>  Tuple(Seq(t.patternList.get.patterns.map(pattern):_*))
      case t: ScWildcardPattern   =>  Wildcard()
      case t: ScCompositePattern  =>  compose(Seq(t.subpatterns : _*))
      case t: ScInfixPattern      =>  ExtractInfix(pattern(t.leftPattern), toTermName(t.reference), t.rightPattern.map(pt=>Seq(pattern(pt))).getOrElse(Nil))
      case t: ScPattern => t ?!
    }
  }

  def template(t: p.toplevel.templates.ScExtendsBlock): m.Template = {
//    def ctor(tpe: types.ScTypeElement) = m.Ctor.Ref.Name(tpe.calcType.canonicalText)
    val exprs   = t.templateBody map (it => Seq(it.exprs.map(expression): _*))
    val members = t.templateBody map (it => Seq(it.members.map(ideaToMeta(_).asInstanceOf[m.Stat]): _*))
    val early   = t.earlyDefinitions map (it => Seq(it.members.map(ideaToMeta(_).asInstanceOf[m.Stat]):_*)) getOrElse Seq.empty
    val parents = t.templateParents map (it => Seq(it.typeElements map ctorParentName :_*)) getOrElse Seq.empty
    val self    = t.selfType match {
      case Some(tpe: ptype.ScType) => m.Term.Param(Nil, m.Term.Name("self"), Some(toType(tpe)), None)
      case None => m.Term.Param(Nil, m.Name.Anonymous(), None, None)
    }
    // FIXME: preserve expression and member order
    val stats = (exprs, members) match {
      case (Some(exp), Some(hld)) => Some(hld ++ exp)
      case (Some(exp), None)  => Some(exp)
      case (None, Some(hld))  => Some(hld)
      case (None, None)       => None
    }
    m.Template(early, parents, self, stats)
  }

  // Java conversion
  def template(arr: Array[PsiMethod]): m.Template = {
    ???
  }

  def newTemplate(t: ScTemplateDefinition): m.Template = {
    val early= t.extendsBlock.earlyDefinitions map (it => Seq(it.members.map(ideaToMeta(_).asInstanceOf[m.Stat]):_*)) getOrElse Seq.empty
    val ctor = t.extendsBlock.templateParents match {
      case Some(parents: p.toplevel.templates.ScClassParents) =>
        parents.constructor match {
          case Some(ctr) => toCtor(ctr)
          case None      => unreachable(s"no constructor found in class ${t.qualifiedName}")
        }
      case Some(other) => unreachable(s"Got something else instead of template parents: $other")
      case None        => unreachable(s"Class ${t.qualifiedName} has no parents")
    }
    val self    = t.selfType match {
      case Some(tpe: ptype.ScType) =>
        m.Term.Param(Nil, m.Term.Name("self")/*.withAttrsFor(t)*/, Some(toType(tpe)), None)
      case None                    =>
        m.Term.Param(Nil, m.Name.Anonymous(), None, None)
    }
    m.Template(early, Seq(ctor), self, None)
  }

  def toCtor(c: ScConstructor) = {
    val ctorRef = c.reference match {
      case Some(ref) => getCtorRef(ref)
      case None => die(s"No reference for ctor ${c.getText}")
    }
    if (c.arguments.isEmpty)
      ctorRef
    else {
      val head = m.Term.Apply(ctorRef, Seq(c.arguments.head.exprs.map(callArgs): _*))
      c.arguments.tail.foldLeft(head)((term, exprList) => m.Term.Apply(term, Seq(exprList.exprs.map(callArgs): _*)))
    }
  }

  def toAnnot(annot: ScAnnotation): m.Mod.Annot = {
    m.Mod.Annot(toCtor(annot.constructor))
  }

  def expression(e: ScExpression): m.Term = {
    import p.expr._
    import p.expr.xml._
    e match {
      case t: ScLiteral =>
        literal(t)
      case t: ScUnitExpr =>
        m.Lit(())
      case t: ScReturnStmt =>
        m.Term.Return(expression(t.expr).get)
      case t: ScBlock =>
        m.Term.Block(Seq(t.statements.map(ideaToMeta(_).asInstanceOf[m.Stat]):_*))
      case t: ScMethodCall =>
        t.withSubstitutionCaching { tp =>
          m.Term.Apply(expression(t.getInvokedExpr), Seq(t.args.exprs.map(callArgs): _*))
        }
      case t: ScInfixExpr =>
        m.Term.ApplyInfix(expression(t.getBaseExpr), toTermName(t.getInvokedExpr), Nil, Seq(expression(t.getArgExpr)))

      case t: ScPrefixExpr =>
        m.Term.ApplyUnary(toTermName(t.operation), expression(t.operand))
      case t: ScPostfixExpr =>
        t.withSubstitutionCaching { tp =>
          m.Term.Apply(m.Term.Select(expression(t.operand), toTermName(t.operation)), Nil)
        }
      case t: ScIfStmt =>
        val unit = m.Lit(())
        m.Term.If(expression(t.condition.get),
            t.thenBranch.map(expression).getOrElse(unit), t.elseBranch.map(expression).getOrElse(unit))
      case t: ScDoStmt =>
        m.Term.Do(t.getExprBody.map(expression).getOrElse(m.Term.Placeholder()),
            t.condition.map(expression).getOrElse(m.Term.Placeholder()))
      case t: ScWhileStmt =>
        m.Term.While(t.condition.map(expression).getOrElse(throw new AbortException(Some(t), "Empty while condition")),
            t.body.map(expression).getOrElse(m.Term.Block(Seq.empty)))
      case t: ScForStatement =>
        m.Term.For(t.enumerators.map(enumerators).getOrElse(Seq.empty),
          t.body.map(expression).getOrElse(m.Term.Block(Seq.empty)))
      case t: ScMatchStmt =>
        m.Term.Match(expression(t.expr.get), Seq(t.caseClauses.map(caseClause):_*))
      case t: ScReferenceExpression if t.qualifier.isDefined =>
        m.Term.Select(expression(t.qualifier.get), toTermName(t))
      case t: ScReferenceExpression =>
        toTermName(t)
      case t: ScSuperReference =>
        m.Term.Super(t.drvTemplate.map(ind).getOrElse(m.Name.Anonymous()), getSuperName(t))
      case t: ScThisReference =>
        m.Term.This(t.reference.map(ind).getOrElse(m.Name.Anonymous()))
      case t: ScNewTemplateDefinition =>
        m.Term.New(newTemplate(t))
      case t: ScFunctionExpr =>
        m.Term.Function(Seq(t.parameters.map(convertParam):_*), expression(t.result).get)
      case t: ScTuple =>
        m.Term.Tuple(Seq(t.exprs.map(expression): _*))
      case t: ScThrowStmt =>
        m.Term.Throw(expression(t.body).getOrElse(throw new AbortException(t, "Empty throw expression")))
      case t@ScTryStmt(tryBlock, catchBlock, finallyBlock) =>
        val fblk = finallyBlock match {
          case Some(b:ScFinallyBlock) => b.expression.map(expression)
          case _ => None
        }
        val res = catchBlock match {
          case Some(ScCatchBlock(clauses)) if clauses.caseClauses.size == 1 =>
            m.Term.TryWithTerm(expression(tryBlock), clauses.caseClause.expr.map(expression).getOrElse(unreachable), fblk)
          case Some(ScCatchBlock(clauses)) =>
            m.Term.TryWithCases(expression(tryBlock), Seq(clauses.caseClauses.map(caseClause):_*), fblk)
          case None =>
            m.Term.TryWithCases(expression(tryBlock), Seq.empty, fblk)
          case _ => unreachable
        }
        res
      case t: ScGenericCall =>
        t ???
      case t: ScConstrExpr =>
        t ???
      case t: ScInterpolatedStringLiteral =>
        t ???
      case t: ScParenthesisedExpr =>
        t ???
      case t: ScTypedStmt =>
        t ???
      case t: ScUnderscoreSection =>
        t ???
      case t: ScXmlExpr =>
        t ???

      case other: ScalaPsiElement => other ?!
    }
  }

  def callArgs(e: ScExpression): m.Term.Arg = {
    e match {
      case t: ScAssignStmt => m.Term.Arg.Named(toTermName(t.getLExpression), expression(t.getRExpression).get)
      case t: ScUnderscoreSection => m.Term.Placeholder()
      case other => expression(e)
    }
  }

  def enumerators(en: ScEnumerators): Seq[m.Enumerator] = {
    def toEnumerator(nm: PsiElement): m.Enumerator = {
      nm match {
        case e: ScGenerator =>
          m.Enumerator.Generator(pattern(e.pattern), expression(e.rvalue))
        case e: ScGuard =>
          m.Enumerator.Guard(e.expr.map(expression).getOrElse(unreachable("guard has no condition")))
        case e: ScEnumerator =>
          m.Enumerator.Val(pattern(e.pattern), expression(e.rvalue))
        case _ => unreachable
      }
    }
    Seq(en.children.filter(elem => elem.isInstanceOf[ScGuard] || elem.isInstanceOf[ScPatterned]).map(toEnumerator).toSeq:_*)
  }

//  def callParams(e: ScExpression): m.Term.Param = {
//    m.Term.Param()
//  }

  def toParams(argss: Seq[ScArgumentExprList]): Seq[Seq[m.Term.Param]] = {
    argss.toStream map { args =>
      args.matchedParameters.toStream map { case (expr, param) =>
        m.Term.Param(param.psiParam.map(p => convertMods(p.getModifierList)).getOrElse(Seq.empty), toParamName(param), Some(toType(param.paramType)), None)
      }
    }
  }

  def expression(tree: Option[ScExpression]): Option[m.Term] = {
    tree match {
      case Some(_: ScUnderscoreSection) => None
      case Some(expr) => Some(expression(expr))
      case None => None
    }
  }

  def getQualifier(q: ScStableCodeReferenceElement): m.Term.Ref = {
    q.pathQualifier match {
      case Some(parent: ScSuperReference) =>
        m.Term.Select(m.Term.Super(m.Name.Anonymous(), m.Name.Anonymous()), toTermName(q))
      case Some(parent: ScThisReference) =>
        m.Term.Select(m.Term.This(m.Name.Anonymous()), toTermName(q))
      case Some(parent:ScStableCodeReferenceElement) =>
        m.Term.Select(getQualifier(parent), toTermName(q))
      case None        => toTermName(q)
      case Some(other) => other ?!
    }
  }

  def getQualifiedReference(q: ScStableCodeReferenceElement) = {
    q.pathQualifier match {
      case None => toTermName(q)
      case Some(_) => m.Term.Select(getQualifier(q), toTermName(q))
    }
  }

  // TODO: WHY?!
  def getCtorRef(q: ScStableCodeReferenceElement): m.Ctor.Ref = {
    q.pathQualifier match {
      case Some(parent: ScSuperReference) =>
        m.Ctor.Ref.Select(m.Term.Super(m.Name.Anonymous(), m.Name.Anonymous()), toCtorName(q))
      case Some(parent: ScThisReference) =>
        m.Ctor.Ref.Select(m.Term.This(m.Name.Anonymous()), toCtorName(q))
      case Some(parent:ScStableCodeReferenceElement) =>
        m.Ctor.Ref.Select(getQualifier(parent), toCtorName(q))
      case None        => toCtorName(q)
      case Some(other) => other ?!
    }
  }

  def imports(t: p.toplevel.imports.ScImportExpr):m.Importer = {
    def selector(sel: p.toplevel.imports.ScImportSelector): m.Importee = {
      if (sel.isAliasedImport && sel.importedName == "_")
        m.Importee.Unimport(ind(sel.reference))
      else if (sel.isAliasedImport)
        m.Importee.Rename(m.Name.Indeterminate(sel.reference.qualName), m.Name.Indeterminate(sel.importedName))
      else
        m.Importee.Name(m.Name.Indeterminate(sel.importedName))
    }
    if (t.selectors.nonEmpty)
      m.Importer(getQualifier(t.qualifier), Seq(t.selectors.map(selector):_*) ++ (if (t.singleWildcard) Seq(m.Importee.Wildcard()) else Seq.empty))
    else if (t.singleWildcard)
      m.Importer(getQualifier(t.qualifier), Seq(m.Importee.Wildcard()))
    else
      m.Importer(getQualifier(t.qualifier), Seq(m.Importee.Name(m.Name.Indeterminate(t.getNames.head))))
  }

  def literal(l: ScLiteral): m.Lit = {
    import p.base.ScLiteral

    import m.Lit
    val res = l match {
      case ScLiteral(i: Integer)              => Lit(i)
      case ScLiteral(l: java.lang.Long)       => Lit(l)
      case ScLiteral(f: java.lang.Float)      => Lit(f)
      case ScLiteral(d: java.lang.Double)     => Lit(d)
      case ScLiteral(b: java.lang.Boolean)    => Lit(b)
      case ScLiteral(c: java.lang.Character)  => Lit(c)
      case ScLiteral(b: java.lang.Byte)       => Lit(b)
      case ScLiteral(s: String)               => Lit(s)
      case ScLiteral(null)                    => Lit(null)
      case _ if l.isSymbol                    => Lit(l.getValue.asInstanceOf[Symbol])
      case other => other ?!
    }
    res
  }

  def toPatternDefinition(t: ScPatternDefinition): m.Tree = {
    def pattern(bp: patterns.ScBindingPattern): m.Pat = {
      m.Pat.Var.Term(toTermName(bp))
    }

    if(t.bindings.exists(_.isVal))
      m.Defn.Val(convertMods(t), Seq(t.bindings.map(pattern):_*), t.declaredType.map(toType(_)), expression(t.expr).get)
    else if(t.bindings.exists(_.isVar))
      m.Defn.Var(convertMods(t), Seq(t.bindings.map(pattern):_*), t.declaredType.map(toType(_)), expression(t.expr))
    else unreachable
  }

  def convertMods(t: p.toplevel.ScModifierListOwner): Seq[m.Mod] = {
    import p.base.ScAccessModifier.Type._
    def extractClassParameter(param: params.ScClassParameter): Seq[m.Mod] = {
      if      (param.isVar) Seq(m.Mod.VarParam())
      else if (param.isVal) Seq(m.Mod.ValParam())
      else Seq.empty
    }
    if (t.getModifierList == null) return Nil // workaround for 9ec0b8a44
    val name = t.getModifierList.accessModifier match {
      case Some(mod) => mod.idText match {
        case Some(qual) => m.Name.Indeterminate(qual)
        case None       => m.Name.Anonymous()
      }
      case None => m.Name.Anonymous()
    }
    val classParam = t match {
      case param: params.ScClassParameter => extractClassParameter(param)
      case _ => Seq.empty
    }
    val common = t.getModifierList.accessModifier match {
      case Some(mod) if mod.access == PRIVATE        => Seq(m.Mod.Private(name))
      case Some(mod) if mod.access == PROTECTED      => Seq(m.Mod.Protected(name))
      case Some(mod) if mod.access == THIS_PRIVATE   => Seq(m.Mod.Private(m.Term.This(name)))
      case Some(mod) if mod.access == THIS_PROTECTED => Seq(m.Mod.Protected(m.Term.This(name)))
      case None => Seq.empty
    }
    val annotations: Seq[m.Mod.Annot] = t match {
      case ah: ScAnnotationsHolder => Seq(ah.annotations.filterNot(_.strip).map(toAnnot):_*)
      case _ => Seq.empty
    }
    val overrideMod = if (t.hasModifierProperty("override")) Seq(m.Mod.Override()) else Nil
    annotations ++ overrideMod ++ common ++ classParam
  }

  // Java conversion
  def convertMods(t: PsiModifierList): Seq[m.Mod] = {
    val mods = scala.collection.mutable.ListBuffer[m.Mod]()
    if (t.hasModifierProperty("private"))    mods += m.Mod.Private(m.Name.Indeterminate.apply("this"))
    if (t.hasModifierProperty("protected"))  mods += m.Mod.Protected(m.Name.Indeterminate.apply("this"))
    if (t.hasModifierProperty("abstract"))   mods += m.Mod.Abstract()
    Seq(mods:_*)
  }

  def convertParamClause(paramss: params.ScParameterClause): Seq[m.Term.Param] = {
    Seq(paramss.parameters.map(convertParam):_*)
  }

  protected def convertParam(param: params.ScParameter): m.Term.Param = {
      val mods = convertMods(param) ++ (if (param.isImplicitParameter) Seq(m.Mod.Implicit()) else Seq.empty)
      if (param.isVarArgs)
        m.Term.Param(mods, toTermName(param), param.typeElement.map(tp => m.Type.Arg.Repeated(toType(tp))), None)
      else
        m.Term.Param(mods, toTermName(param), param.typeElement.map(toType), None)
  }
}

package org.jetbrains.plugins.scala.meta.trees

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScClassParents
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.language.postfixOps
import scala.meta.internal.ast.Term.Param
import scala.{Seq => _}
import scala.collection.immutable.Seq
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.{api => p}
import org.jetbrains.plugins.scala.lang.psi.{types => ptype}

import scala.meta.internal.{ast=>m}
import scala.meta.internal.{hygiene => h}

trait TreeAdapter {
  self: Converter =>

  def ideaToMeta(tree: ScalaPsiElement): m.Tree = {
    tree match {
      case t: p.statements.ScValueDeclaration =>
        m.Decl.Val(convertMods(t), t.getIdList.fieldIds.toStream map { it => m.Pat.Var.Term(m.Term.Name(it.name))}, toType(t.typeElement.get.calcType))
      case t: p.statements.ScVariableDeclaration =>
        m.Decl.Var(convertMods(t), t.getIdList.fieldIds.toStream map { it => m.Pat.Var.Term(m.Term.Name(it.name))}, toType(t.typeElement.get.calcType))
      case t: p.statements.ScTypeAliasDeclaration =>
        m.Decl.Type(convertMods(t), m.Type.Name(t.name), t.typeParameters.toStream map toType, typeBounds(t))
      case t: p.statements.ScTypeAliasDefinition =>
        m.Defn.Type(convertMods(t), toName(t), t.typeParameters.toStream map toType, toType(t.aliasedType))
      case t: p.statements.ScFunctionDeclaration =>
        m.Decl.Def(convertMods(t), m.Term.Name(t.name), t.typeParameters.toStream map toType, t.paramClauses.clauses.toStream.map(convertParams), returnType(t.returnType))
      case t: p.statements.ScPatternDefinition =>
        patternDefinition(t)
      case t: p.statements.ScVariableDefinition =>
        def pattern(bp: p.base.patterns.ScBindingPattern) = m.Pat.Var.Term(m.Term.Name(bp.name))
        m.Defn.Var(convertMods(t), t.bindings.toStream.map(pattern), t.declaredType.map(toType), expression(t.expr))
      case t: p.statements.ScFunctionDefinition =>
        m.Defn.Def(convertMods(t), m.Term.Name(t.name),
          t.typeParameters.toStream map toType,
          t.paramClauses.clauses.toStream.map(convertParams),
          t.definedReturnType.map(toType).toOption,
          expression(t.body).get
        )
      case t: p.toplevel.typedef.ScTrait => toTrait(t)
      case t: p.toplevel.typedef.ScClass => toClass(t)
      case t: p.toplevel.typedef.ScObject => toObject(t)
      case t: p.expr.ScExpression => expression(Some(t)).get
      case t: p.toplevel.imports.ScImportStmt => m.Import(t.importExprs.toStream.map(imports))
      case other => other ?!
    }
  }

  def toTrait(t: p.toplevel.typedef.ScTrait) = m.Defn.Trait(
    convertMods(t),
    toName(t),
    t.typeParameters.toStream map toType,
    m.Ctor.Primary(Nil, m.Ctor.Ref.Name("this"), Nil),
    template(t.extendsBlock)
  )

  def toClass(c: p.toplevel.typedef.ScClass) = m.Defn.Class(
    convertMods(c),
    toName(c),
    c.typeParameters.toStream map toType,
    ctor(c.constructor),
    template(c.extendsBlock)
  )

  def toObject(o: p.toplevel.typedef.ScObject) = m.Defn.Object(
    convertMods(o),
    toName(o),
    m.Ctor.Primary(Nil, m.Ctor.Ref.Name("this"), Nil),
    template(o.extendsBlock)
  )

  def ctor(pc: Option[p.base.ScPrimaryConstructor]): m.Ctor.Primary = {
    pc match {
      case None => throw new RuntimeException("no primary constructor in class")
      case Some(ctor) => m.Ctor.Primary(convertMods(ctor), toName(ctor), ctor.parameterList.clauses.toStream.map(convertParams))
    }
  }

  def caseClause(c: p.base.patterns.ScCaseClause): m.Case = {
    m.Case(pattern(c.pattern.get), c.guard.map(it => expression(it.expr.get)), expression(c.expr.get).asInstanceOf[m.Term.Block])
  }

  def pattern(pt: p.base.patterns.ScPattern): m.Pat = {
    import p.base.patterns._
    import m.Pat._
    def compose(lst: Seq[ScPattern]): m.Pat = lst match {
      case x :: Nil => pattern(x)
      case x :: xs  => Alternative(pattern(x), compose(xs))
    }
    // WHY??(((
    def arg(pt: p.base.patterns.ScPattern): m.Pat.Arg = pt match {
      case t: ScSeqWildcard       =>  Arg.SeqWildcard()
      case t: ScWildcardPattern   =>  Wildcard()
      case t: ScPattern           => pattern(t)
    }
    pt match {
      case t: ScReferencePattern  =>  Var.Term(toName(t))
      case t: ScConstructorPattern=>  Extract(ref(t.ref), Nil, t.args.patterns.toStream.map(arg))
      case t: ScNamingPattern     =>  Bind(Var.Term(toName(t)), arg(t.named))
      case t@ ScTypedPattern(te: p.base.types.ScWildcardTypeElement) => Typed(if (t.isWildcard) Wildcard() else Var.Term(toName(t)), Type.Wildcard())
      case t@ ScTypedPattern(te)  =>  Typed(if (t.isWildcard) Wildcard() else Var.Term(toName(t)), toType(te).patTpe)
      case t: ScLiteralPattern    =>  literal(t.getLiteral)
      case t: ScTuplePattern      =>  Tuple(t.patternList.get.patterns.toStream.map(pattern))
      case t: ScWildcardPattern   =>  Wildcard()
      case t: ScCompositePattern  =>  compose(Seq(t.subpatterns : _*))
      case t: ScInfixPattern      =>  ExtractInfix(pattern(t.leftPattern), ref(t.refernece), t.rightPattern.map(pt=>Seq(pattern(pt))).getOrElse(Nil))
      case t: ScPattern => t ?!
    }
  }

  def template(t: p.toplevel.templates.ScExtendsBlock): m.Template = {
    def ctor(tpe: p.base.types.ScTypeElement) = m.Ctor.Ref.Name(tpe.calcType.canonicalText)
    val exprs   = t.templateBody map (it => Seq(it.exprs.map(expression): _*))
    val holders = t.templateBody map (it => Seq(it.holders.map(ideaToMeta(_).asInstanceOf[m.Stat]): _*))
    val early   = t.earlyDefinitions map (it => it.members.toStream.map(ideaToMeta(_).asInstanceOf[m.Stat])) getOrElse Seq.empty
    val parents = t.templateParents map (it => it.typeElements.toStream map ctor) getOrElse Seq.empty
    val self    = t.selfType match {
      case Some(tpe: ptype.ScType) => m.Term.Param(Nil, m.Term.Name("self"), Some(toType(tpe)), None)
      case None => m.Term.Param(Nil, m.Name.Anonymous(), None, None)
    }
    val stats = (exprs, holders) match {
      case (Some(exp), Some(hld)) => Some(hld ++ exp)
      case (Some(exp), None)  => Some(exp)
      case (None, Some(hld))  => Some(hld)
      case (None, None)       => None
    }
    m.Template(early, parents, self, stats)
  }

  def template(t: p.toplevel.typedef.ScTemplateDefinition): m.Template = {
    val early   = t.extendsBlock.earlyDefinitions map (it => it.members.toStream.map(ideaToMeta(_).asInstanceOf[m.Stat])) getOrElse Seq.empty
    val ctor = t.extendsBlock.templateParents match {
      case Some(parents: p.toplevel.templates.ScClassParents) => toCtor(parents.constructor.get)
    }
    val self    = t.selfType match {
      case Some(tpe: ptype.ScType) => m.Term.Param(Nil, m.Term.Name("self"), Some(toType(tpe)), None)
      case None => m.Term.Param(Nil, m.Name.Anonymous(), None, None)
    }
    m.Template(early, Seq(ctor), self, None)
  }

  def toCtor(c: p.base.ScConstructor) = {
    if (c.arguments.isEmpty)
      toName(c)
    else
      m.Term.Apply(toName(c), c.args.get.exprs.toStream.map(callArgs(_)))
  }

  def member(t: p.toplevel.typedef.ScMember): m.Stat = {
    m.Ctor.Ref.Name("B") // FIXME: what
  }

  def expression(e: p.expr.ScExpression): m.Term = {
    import p.expr._
    e match {
      case t: p.base.ScLiteral => literal(t)
      case t: ScUnitExpr => m.Lit.Unit()
      case t: ScReturnStmt => m.Term.Return(expression(t.expr).get)
      case t: ScBlock => m.Term.Block(t.statements.toStream.map(ideaToMeta(_).asInstanceOf[m.Stat]))
      case t: ScMethodCall => m.Term.Apply(toName(t.getInvokedExpr), t.args.exprs.toStream.map(callArgs))
      case t: ScInfixExpr => m.Term.ApplyInfix(expression(t.getBaseExpr), toName(t.getInvokedExpr), Nil, Seq(expression(t.getArgExpr)))
      case t: ScPrefixExpr => m.Term.ApplyUnary(toName(t.operation), expression(t.operand))
      case t: ScIfStmt => m.Term.If(expression(t.condition.get),
        t.thenBranch.map(expression).getOrElse(m.Lit.Unit()), t.elseBranch.map(expression).getOrElse(m.Lit.Unit()))
      case t: ScMatchStmt => m.Term.Match(expression(t.expr.get), t.caseClauses.toStream.map(caseClause))
      case t: ScReferenceExpression => toName(t)
      case t: ScNewTemplateDefinition => m.Term.New(template(t))
      case other: ScalaPsiElement => other ?!
    }
  }

  def callArgs(e: p.expr.ScExpression) = {
    e match {
      case t: p.expr.ScAssignStmt => m.Term.Arg.Named(toName(t.getLExpression), expression(t.getRExpression).get)
      case t: p.expr.ScUnderscoreSection => m.Term.Placeholder()
      case other => expression(e)
    }
  }

  def expression(tree: Option[p.expr.ScExpression]): Option[m.Term] = {
    tree match {
      case Some(_: p.expr.ScUnderscoreSection) => None
      case Some(expr) => Some(expression(expr))
      case None => None
    }
  }

  def imports(t: p.toplevel.imports.ScImportExpr):m.Import.Clause = {
    def qual(q: p.base.ScStableCodeReferenceElement): m.Term.Ref = {
      q.pathQualifier match {
        case Some(parent: p.expr.ScSuperReference) =>
          m.Term.Select(m.Term.Super(m.Name.Anonymous(), m.Name.Anonymous()), ref(q))
        case Some(parent: p.expr.ScThisReference) =>
          m.Term.Select(m.Term.This(m.Name.Anonymous()), ref(q))
        case Some(parent:p.base.ScStableCodeReferenceElement) =>
          m.Term.Select(qual(parent), ref(q))
        case Some(other) => other ?!
        case None         => ref(q)
      }
    }
    def selector(sel: p.toplevel.imports.ScImportSelector): m.Import.Selector = {
      if (sel.isAliasedImport && sel.importedName == "_")
        m.Import.Selector.Unimport(ind(sel.reference))
      else if (sel.isAliasedImport)
        m.Import.Selector.Rename(m.Name.Indeterminate(sel.reference.qualName), m.Name.Indeterminate(sel.importedName))
      else
        m.Import.Selector.Name(m.Name.Indeterminate(sel.importedName))
    }
    if (t.selectors.nonEmpty)
      m.Import.Clause(qual(t.qualifier), Seq(t.selectors.map(selector):_*) ++ (if (t.singleWildcard) Seq(m.Import.Selector.Wildcard()) else Seq.empty))
    else if (t.singleWildcard)
      m.Import.Clause(qual(t.qualifier), Seq(m.Import.Selector.Wildcard()))
    else
      m.Import.Clause(qual(t.qualifier), Seq(m.Import.Selector.Name(m.Name.Indeterminate(t.getNames.head))))
  }

  def literal(l: p.base.ScLiteral): m.Lit = {
    import p.base.ScLiteral
    import m.Lit
    l match {
      case ScLiteral(i: Integer)              => Lit.Int(i)
      case ScLiteral(l: java.lang.Long)       => Lit.Long(l)
      case ScLiteral(f: java.lang.Float)      => Lit.Float(f)
      case ScLiteral(d: java.lang.Double)     => Lit.Double(d)
      case ScLiteral(b: java.lang.Boolean)    => Lit.Bool(b)
      case ScLiteral(c: java.lang.Character)  => Lit.Char(c)
      case ScLiteral(s: String)               => Lit.String(s)
      case ScLiteral(null)                    => Lit.Null()
      case _ if l.isSymbol                    => Lit.Symbol(l.getValue.asInstanceOf[Symbol])
      case other => other ?!
    }
  }

  def patternDefinition(t: p.statements.ScPatternDefinition): m.Tree = {
    def pattern(bp: p.base.patterns.ScBindingPattern): m.Pat = {
      m.Pat.Var.Term(m.Term.Name(bp.name))
    }

    if(t.bindings.exists(_.isVal))
      m.Defn.Val(convertMods(t), t.bindings.toStream.map(pattern), t.declaredType.map(toType), expression(t.expr).get)
    else if(t.bindings.exists(_.isVar))
      m.Defn.Var(convertMods(t), t.bindings.toStream.map(pattern), t.declaredType.map(toType), expression(t.expr))
    else ???
  }

  def convertMods(t: p.toplevel.ScModifierListOwner): Seq[m.Mod] = {
    import p.base.ScAccessModifier.Type._
    def extractClassParameter(param: p.statements.params.ScClassParameter): Seq[m.Mod] = {
      if      (param.isVar) Seq(m.Mod.VarParam())
      else if (param.isVal) Seq(m.Mod.ValParam())
      else Seq.empty
    }
    val name = t.getModifierList.accessModifier match {
      case Some(mod) => mod.idText match {
        case Some(qual) => m.Name.Indeterminate(qual)
        case None       => m.Name.Anonymous()
      }
      case None => m.Name.Anonymous()
    }
    val classParam = t match {
      case param: p.statements.params.ScClassParameter => extractClassParameter(param)
      case _ => Seq.empty
    }
    val common = t.getModifierList.accessModifier match {
      case Some(mod) if mod.access == PRIVATE        => Seq(m.Mod.Private(name))
      case Some(mod) if mod.access == PROTECTED      => Seq(m.Mod.Protected(name))
      case Some(mod) if mod.access == THIS_PRIVATE   => Seq(m.Mod.Private(m.Term.This(name)))
      case Some(mod) if mod.access == THIS_PROTECTED => Seq(m.Mod.Protected(m.Term.This(name)))
      case None => Seq.empty
    }
    val overrideMod = if (t.hasModifierProperty("override")) Seq(m.Mod.Override()) else Nil
    overrideMod ++ common ++ classParam
  }

  def convertParams(params: p.statements.params.ScParameterClause): Seq[Param] = {
    params.parameters.toStream.map {
        param =>
          val mods = convertMods(param) ++ (if(param.isImplicitParameter) Seq(m.Mod.Implicit()) else Seq.empty)
          if(param.isVarArgs)
           m.Term.Param(mods, m.Term.Name(param.name),  param.typeElement.map(tp=>m.Type.Arg.Repeated(toType(tp))), None)
          else
            m.Term.Param(mods, m.Term.Name(param.name), param.typeElement.map(toType), None)
      }
  }

}

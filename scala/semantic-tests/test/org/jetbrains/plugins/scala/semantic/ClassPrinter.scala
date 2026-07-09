// Copy of org.jetbrains.plugins.scala.text.ClassPrinter

package org.jetbrains.plugins.scala.semantic

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, Parent, PsiClassExt, PsiElementExt, PsiMemberExt}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSelfTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScLiteral, ScModifierList, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.*
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause.{TermClause, TypeClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExtension, ScFunction, ScFunctionDefinition, ScSignatureClause, ScTypeAlias, ScTypeAliasDefinition, ScValue, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScGiven, ScGivenDefinition, ScMember, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScPackaging, ScTypeBoundsOwner, ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType.isValueClass
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScLiteralType, ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.ScalaFeatures.forPsiOrDefault
import org.jetbrains.plugins.scala.semantic.ClassPrinter.{Keywords, isIdentifier}

class ClassPrinter(isScala3: Boolean, extendsSeparator: String = " ", withPrivate: Boolean = true, normalize: Boolean = false) {
  def textOf(e: PsiElement): String = e match {
    case cls: ScTypeDefinition =>
      val sb = new StringBuilder()
      printTo(sb, cls)
      sb.toString
    case f: ScFunction => textOf(f, "")
    case v: ScBindingPattern => textOf(v.nameContext.asInstanceOf[ScValueOrVariable], v, "")
    case t: ScTypeAlias => textOf(t, "")
  }

  def printTo(sb: StringBuilder, cls: ScTypeDefinition): Unit = printTo(sb, cls, "")

  private def printTo(sb: StringBuilder, cls: ScTypeDefinition, indent: String): Unit = {
    val annotations = cls.annotations.map(a => "\n" + indent + textOf(a)).mkString

    val modifiers = {
      val s = textOf(cls.getModifierList)
      if (normalize && cls.isInstanceOf[ScClass] && (cls.hasModifierProperty("implicit") || isValueClass(cls))) s.replace("final ", "")
      else if (normalize && cls.isInstanceOf[ScObject] && cls.hasModifierProperty("case")) s.replace("final ", "")
      else s
    }

    val keyword = cls match {
      case _: ScEnum => "enum"
      case _: ScTrait => "trait"
      case _: ScClass => "class"
      case _: ScObject => "object"
      case _: ScGiven => "given"
      case _ => ""
    }

    val isGiven = cls.isInstanceOf[ScGiven]

    val isAnonymous = isGiven && cls.name.startsWith("given_") // .isAnonymous?

    val name = if (isAnonymous) "" else normalized(cls.name)

    val tps = if (cls.typeParameters.isEmpty) "" else cls.typeParameters.map(textOf).mkString("[", ", ", "]")

    // TODO Don't add "Foo.this." in primary constructors, see ScalaTypePresentation.innerTypeText, SCL-25555
    val ps = cls.constructors.filterByType[ScPrimaryConstructor].map(textOf(_, inCaseClass = modifiers.contains("case")).replace(name + ".this.", "")).mkString

    val parents = {
      val superTypes = cls.extendsBlock.templateParents.map(_.superTypes).getOrElse(Seq.empty)
      val classParent =
        if (normalize && isScala3 && !cls.isInterface && cls.superClass.isEmpty) cls.allSupers.find(!_.isInterface).filter(_.qualifiedName != "java.lang.Object").map(ScDesignatorType(_)).toList
        else Seq.empty
      // TODO Don't add "Foo.this." in class parents, see ScalaTypePresentation.innerTypeText, SCL-25555
      if (superTypes.isEmpty) "" else (if (isGiven) (if (isAnonymous && tps.isEmpty && ps.isEmpty) "" else ": ") else s"${extendsSeparator}extends ") +
        (classParent ++ superTypes).map(textOf(_, parens = 1).replace(name + ".this.", "")).mkString(if (cls.isScala3 && !isGiven) ", " else s"${extendsSeparator}with ")
    }

    val derivations = {
      val refs = cls.extendsBlock.derivesClause.map(_.derivedReferences).getOrElse(Seq.empty)
      val fqns = refs.map(_.resolve()).collect { case f: ScTemplateDefinition => f.qualifiedName }
      if (fqns.isEmpty) "" else s"${extendsSeparator}derives " + fqns.mkString(", ")
    }

    val selfType = cls.selfType.map(t => s" ${cls.selfTypeElement.map(_.name).getOrElse("this")}: " + textOf(t) + " =>").mkString

    val givenClauses = cls match {
      case g: ScGivenDefinition => g.clauses.map(_.clauses).getOrElse(Seq.empty).map(textOf(_, inPrivateConstructor = false, inCaseClass = false)).mkString
      case _ => ""
    }

    sb ++= annotations + "\n" + indent + modifiers + keyword + " " + name + tps + ps + givenClauses + parents + derivations + (if (isGiven) " with" else "") + " {" + selfType

    val previousLength = sb.length

    printTo(sb, cls.extendsBlock, indent)

    cls match {
      case e: ScEnum =>
        e.cases.foreach { c =>
          sb ++= textOf(c, indent)
        }
      case _ =>
    }

    if (sb.length > previousLength) {
      sb ++= indent + "}"
    } else {
      if (selfType.isEmpty) {
        sb.setLength(sb.length - 2)
      } else {
        sb ++= "\n"
        sb ++= indent + "}"
      }
    }

    sb ++= "\n"
  }

  private def printTo(sb: StringBuilder, extendsBlock: ScExtendsBlock, indent: String): Unit =
    extendsBlock.members.filter(m => withPrivate || !isPrivate(m)).foreach {
      case f: ScFunction =>
        sb ++= textOf(f, indent)
      case v: ScValueOrVariable =>
        sb ++= textOf(v, v.declaredElements.head, indent)
      case t: ScTypeAlias =>
        sb ++= textOf(t, indent)
      case t: ScExtension =>
        sb ++= textOf(t, indent)
      case td: ScTypeDefinition =>
        printTo(sb, td, indent + "  ")
      case _ =>
    }

  private def isPrivate(e: ScModifierListOwner): Boolean =
    e.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)

  private def textOf(pc: ScPrimaryConstructor, inCaseClass: Boolean): String = {
    val modifiers = textOf(pc.getModifierList)
    val inPrivateConstructor = isPrivate(pc)
    val clauses = {
      val signatureClausesInParameterList = pc.signatureClauses.filter {
        case TypeClause(clause) => !pc.leadingTypeParametersClause.contains(clause)
        case TermClause(_)      => true
      }
      textOf(signatureClausesInParameterList, inPrivateConstructor, inCaseClass)
    }
    val annotations = pc.annotations.map(textOf(_, emptyParens = clauses.nonEmpty && modifiers.isEmpty)).mkString(" ")
    val s = (if (annotations.isEmpty) "" else " " + annotations) +
      (if (modifiers.isEmpty) "" else " " + modifiers) +
      (if (annotations.nonEmpty && modifiers.isEmpty) " " else "") +
      (if ((annotations.nonEmpty || modifiers.nonEmpty) && clauses.isEmpty) "()" else clauses)
    if (normalize && !inCaseClass && s == "()") "" else s
  }

  private def textOf(c: ScEnumCase, indent: String): String = {
    val tps = if (c.typeParameters.isEmpty) "" else c.typeParameters.map(textOf).mkString("[", ", ", "]")
    val ps = c.constructors.filterByType[ScPrimaryConstructor].map(textOf(_, inCaseClass = true)).mkString
    "\n" + indent + "  " + "case " + c.name + tps + ps + "\n"
  }

  private def textOf(f: ScFunction, indent: String): String = {
    val isGiven = f.isInstanceOf[ScGiven]
    val isAnonymous = isGiven && f.name.startsWith("given_") // .isAnonymous?
    val annotations = f.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(f.getModifierList)
    val keyword = if (isGiven) "given " else "def "
    val name = if (isAnonymous) "" else normalized(f.name)
    val signature = textOf(f.signatureClauses, inPrivateConstructor = false, inCaseClass = false)
    val tpe = if (f.isConstructor) "" else (if (signature.isEmpty) spaceAfter(name) else "") + (if (isAnonymous && signature.isEmpty) "" else ": ") + textOf(f.returnType)
    val rhs = f match {
      case ScFunctionDefinition.withBody(body) =>
        val rhs = textOfStatement(normalized(body), indent + "  ")
        if (rhs.startsWith("{")) " = " + rhs else if (!f.isLocal) " = " + rhs else " = " + rhs.trim
      case _ => ""
    }
    annotations + "\n" + indent + "  " + modifiers + keyword + name + signature + tpe + rhs + "\n"
  }

  private def textOfStatement(s: ScBlockStatement, indent: String): String = s match {
    case b: ScBlock => "{" + b.statements.filter(!_.is[ScImportStmt]).map(s => textOfStatement(s, indent)).mkString("") + "\n" + indent + "}"
    case t: ScTypeDefinition =>
      val sb = new StringBuilder()
      printTo(sb, t)
      sb.toString.stripSuffix("\n")
    case f: ScFunction => textOf(f, indent).stripSuffix("\n")
    case v: ScValueOrVariable => v.declaredElements.headOption.map(textOf(v, _, indent).stripSuffix("\n")).getOrElse("")
    case t: ScTypeAlias => textOf(t, indent).stripSuffix("\n")
    case e: ScExpression => "\n" + indent + "  " + textOfExpression(e, indent)
    case _ => "<stmt>"
  }

  private def textOfExpression(e: ScExpression, indent: String): String = {
    val text = e match {
      case b: ScBlockExpr => "{" + b.statements.map(s => textOfStatement(s, indent + "  ")).mkString("") + "\n" + indent + "  " + "}"
      case p: ScParenthesisedExpr => p.innerElement.map(textOfExpression(_, indent)).getOrElse("")
      case u: ScUnitExpr => "()"
      case u: ScThisReference => "this"
      case s: ScSuperReference => "super" + (s.staticSuper.map("[" + textOf(_) + "]").getOrElse(""))
      case l: ScLiteral => if (l.getValue == null) "null" else l.literalType.asInstanceOf[ScLiteralType].value.presentation
      case e: ScUnderscoreSection => "_"
      case e: ScIf =>
        "if (" + e.condition.map(e => textOfExpression(normalized(e), indent)).getOrElse("") + ") " + e.thenExpression.map(e => textOfExpression(normalized(e), indent)).getOrElse("") + (e.elseExpression match {
          case Some(e) => " else " + textOfExpression(normalized(e), indent)
          case None => ""
        })
      case e: ScTry =>
        "try " + e.expression.map(e => textOfExpression(normalized(e), indent)).getOrElse("") +
          e.catchBlock.flatMap(_.expression).map(e => textOfExpression(normalized(e), indent)).map(" catch " + _).getOrElse("") +
          e.finallyBlock.flatMap(_.expression).map(e => textOfExpression(normalized(e), indent)).map(" finally " + _).getOrElse("")
      case e: ScWhile =>
        "while (" + e.condition.map(e => textOfExpression(normalized(e), indent)).getOrElse("") + ") " +
          e.expression.map(e => textOfExpression(normalized(e), indent)).getOrElse("")
      case mi: MethodInvocation =>
        val targs = mi match {
          case TypeArgumentOwner.CallWithInferredTypeArguments(hints) => hints match {
            case Seq(TypeArgumentOwner.TypeArgumentHint.Bracketed(anchor, typeArguments)) => "[" + typeArguments.map(textOf(_)).mkString(", ") + "]"
            case _ => ""
          }
          case _ => ""
        }
        val explicitImplicitArguments = mi.matchedParameters.headOption.exists {
          case (_, param) => param.psiParam.exists {
            case p: ScParameter => p.isInClauseWithImplicit || p.isInClauseWithUsing
            case _ => false
          }
        }
        val invokedExpr = mi.getEffectiveInvokedExpr
        mi.thisExpr.filter(!invokedExpr.elements.contains(_)).map(textOfExpression(_, indent)).map(_ + ".").getOrElse("") +
          textOfExpression(invokedExpr, indent) + targs + "(" + (if (explicitImplicitArguments) "using " else "") + mi.argumentExpressions.map(textOfExpression(_, indent)).mkString(", ") + ")"
      case gc: ScGenericCall =>
        textOfExpression(gc.referencedExpr, indent) + "[" + gc.typeArguments.map(ta => textOf(ta.`type`())).mkString(", ") + "]"
      case sc: ScAssignment =>
        textOfExpression(sc.leftExpression, indent) + " = " + sc.rightExpression.map(textOfExpression(_, indent)).getOrElse("")
      case r: ScReferenceExpression => r.qualifier match {
        case Some(q) => textOfExpression(q, indent) + "." + r.refName
        case None =>
          val prefix = r.bind().map(_.getActualElement).orNull match {
            case e: ScSelfTypeElement => e.name
            case e: ScNamedElement => e.nameContext match {
              case m: ScMember if !m.isLocal && !m.isTopLevel =>
                if (e.getContainingFile == r.getContainingFile) (if (m.containingClass.name == "<anonymous>") "this." + r.refName else m.containingClass.name + ".this." + r.refName)
                else m.qualifiedNameOpt.getOrElse(r.refName)
              case _ => r.refName
            }
            case _ => r.refName
          }
          r.bind() match {
            case Some(r) if r.element != r.getActualElement && r.element.getName == "apply" =>
              prefix + ".apply"
            case _ => prefix
          }
      }
      case t: ScThrow => "throw " + textOfExpression(t.expression.get, indent)
      case e: ScNewTemplateDefinition =>
        "new " + e.firstConstructorInvocation
          .map(ci => textOf(ci.typeElement.`type`().get) + ci.arguments.map(args => "(" + args.exprs.map(textOfExpression(_, indent)).mkString(", ") + ")").mkString)
          .getOrElse("") + (if (!e.extendsBlock.members.exists(m => withPrivate || !isPrivate(m))) "" else
          " {" + {
            val sb = new StringBuilder()
            printTo(sb, e.extendsBlock, indent + "  ")
            if (sb.nonEmpty) sb ++= indent + "  "
            sb.toString
          } + "}")
      case e: ScFunctionExpr =>
        "(" + e.parameters.map(p => p.name + ": " + textOf(p.`type`().get)).mkString(", ") + ") => " + e.result.map(textOfExpression(_, indent)).getOrElse("")
      case e: ScTuple =>
        "scala.Tuple" + e.exprs.length + ".apply[" + e.exprs.map(e => textOf(e.`type`().get)).mkString(", ") + "](" + e.exprs.map(textOfExpression(_, indent)).mkString(", ") + ")"
      case e => "<expr>"
    }

    text + textOfImplicitArguments(e.findImplicitArguments, e)
  }

  private def textOfImplicitArguments(args: Seq[ImplicitArgumentsClause], place: PsiElement): String = args
    .map { clause =>
      clause.args.map(arg =>
        val typeArgText = arg.element match {
          case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
            owner.typeParameters.map(tp => arg.substitutor(TypeParameterType(tp))).map(textOf(_)).mkString("[", ", ", "]")
          case _ => ""
        }
        val prefix = arg.fromType match {
          case Some(tpe) =>
            tpe.canonicalText(TypePresentationContext(place))(using Context(place)).stripPrefix("_root_.").stripSuffix(".type") + "." + arg.element.asInstanceOf[ScNamedElement].name
          case _ =>
            arg.element match {
              case e: ScNamedElement => e.nameContext match {
                case m: ScMember if !m.isLocal && !m.isTopLevel =>
                  if (e.getContainingFile == place.getContainingFile) (if (m.containingClass.name == "<anonymous>") "this." + e.name else m.containingClass.name + ".this." + e.name)
                  else m.qualifiedNameOpt.getOrElse(e.name)
                case _ => e.name
              }
            }
        }
        prefix + typeArgText + textOfImplicitArguments(arg.implicitArguments, place)).mkString(", ")
    }
    .map("(using " + _ + ")").mkString

  private def normalized(e: ScExpression): ScExpression = e match {
    case b: ScBlock if normalize => b.statements match {
      case Seq(e: ScExpression) => e
      case statements if statements.length > 1 && statements.init.forall(_.is[ScImportStmt]) => statements.last.asInstanceOf[ScExpression]
      case _ => b
    }
    case e => e
  }

  private def textOf(e: ScExtension, indent: String): String = {
    val signature = textOf(e.signatureClauses, inPrivateConstructor = false, inCaseClass = false)
    val methods = e.extensionMethods.map(textOf(_, indent + "  ")).mkString
    "\n" + indent + "  " + "extension " + signature + methods
  }

  private def textOf(v: ScValueOrVariable, symbol: ScTypedDefinition, indent: String): String = {
    val annotations = v.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(v.getModifierList)
    val keyword = if (v.isInstanceOf[ScValue]) "val " else "var "
    val symbolType = symbol.`type`()
    val isConstant = (v.hasModifierPropertyScala("final") || v.hasModifierPropertyScala("inline")) && !v.hasExplicitType && !v.isAbstract && symbolType.exists(canBeTypeOfConstant)
    val name = normalized(symbol.name)
    val tpe = if (isConstant) "" else (spaceAfter(name) + ": " + textOf(symbolType))
    val rhs = if (isConstant) (" = " + v.asInstanceOf[ScValueOrVariableDefinition].expr.map(_.getText).getOrElse("")) else v match {
      case ScValueOrVariableDefinition.withExpr(expr) =>
        val rhs = textOfStatement(normalized(expr), indent + "  ")
        if (rhs.startsWith("{")) " = " + rhs else if (!v.isLocal) " = " + rhs else " = " + rhs.trim
      case _ => ""
    }
    annotations + "\n" + indent + "  " + modifiers + keyword + name + tpe + rhs + "\n"
  }

  private def canBeTypeOfConstant(tpe: ScType): Boolean = tpe match {
    case _: ScLiteralType => true
    case t if t.isPrimitive => true
    case t if t.isNull => true
    case ScDesignatorType(cls: PsiClass) if cls.getQualifiedName == "java.lang.String" => true
    case _ => false
  }

  def printTo(sb: StringBuilder, alias: ScTypeAlias): Unit = {
    sb ++= textOf(alias, "").split("\n").map(_.stripPrefix("  ")).mkString("\n")
    sb ++= "\n"
  }

  private def textOf(t: ScTypeAlias, indent: String): String = {
    val annotations = t.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(t.getModifierList)
    val name = normalized(t.name)
    val tps = if (t.typeParameters.isEmpty) "" else t.typeParameters.map(textOf).mkString("[", ", ", "]")
    val bounds = textOfBoundsIn(t)
    val rhs = t match {
      case definition: ScTypeAliasDefinition if !(normalize && definition.isOpaque) => " = " + textOf(definition.aliasedType)
      case _ => ""
    }
    annotations + "\n" + indent + "  " + modifiers + "type " + name + tps + bounds + rhs + "\n"
  }

  private def textOf(p: ScTypeParam): String = {
    val annotations = p.annotations.map(textOf).mkString(" ")
    val variance = if (p.isCovariant) "+" else if (p.isContravariant) "-" else ""
    val name = normalized(p.name)
    val clauses = p.typeParametersClause.map(_.typeParameters.map(textOf).mkString("[", ", ", "]")).mkString
    val typeBounds = textOfBoundsIn(p)
    val contextBound = p.contextBound.map(t => ": " + textOf(t)).mkString
    (if (annotations.isEmpty) "" else annotations + " ") + variance + name + clauses + typeBounds + contextBound
  }

  private def textOfBoundsIn(o: ScTypeBoundsOwner): String = {
    val lower = o.lowerTypeElement.flatMap(_.`type`().toOption).map(textOf(_)).filter(_ != "_root_.scala.Nothing").map(" >: " + _).getOrElse("")
    val upper = o.upperTypeElement.flatMap(_.`type`().toOption).map(textOf(_)).filter(_ != "_root_.scala.Any").map(" <: " + _).getOrElse("")
    lower + upper
  }

  private def textOf(clause: ScParameterClause, inPrivateConstructor: Boolean, inCaseClass: Boolean): String = {
    val ps = clause.parameters.filter(p => withPrivate || !inPrivateConstructor || ((inCaseClass || p.isVal || p.isVar) && !isPrivate(p)))
    ps.map(textOf(_, inCaseClass)).mkString(if (ps.nonEmpty) (if (clause.hasImplicitKeyword) "(implicit " else if (clause.hasUsingKeyword) "(using " else "(") else "(", ", ", ")")
  }

  private def textOf(clause: ScTypeParamClause): String =
    clause.typeParameters.map(textOf).mkString("[", ", ", "]")

  private def textOf(
    signatureClauses: Seq[ScSignatureClause],
    inPrivateConstructor: Boolean,
    inCaseClass: Boolean
  ): String = {
    var isFirstTermClause = true

    signatureClauses.map {
      case TypeClause(clause) =>
        textOf(clause)
      case TermClause(clause) =>
        val text = textOf(clause, inPrivateConstructor, inCaseClass && isFirstTermClause)
        isFirstTermClause = false
        text
    }.mkString
  }

  private def textOf(p: ScParameter, inCaseClass: Boolean): String = {
    val annotations = p.annotations.map(textOf).mkString(" ")
    val modifiers = {
      val s = textOf(p.getModifierList)
      if (withPrivate) s else s.replace("private[this] ", "").replace("private ", "")
    }
    val keyword =
      if (withPrivate || !isPrivate(p)) if (p.isVal) (if (!normalize || !(inCaseClass && modifiers.isEmpty)) "val " else "") else if (p.isVar) "var " else ""
      else ""
    val name = normalized(p.name)
    val byName = if (p.isCallByNameParameter) "=> " else ""
    val tpe = textOf(p.`type`())
    val isAnonymous = p.isAnonymous
    val repeated = if (p.isRepeatedParameter) "*" else ""
    val default = if (p.baseDefaultParam) " = ???" else ""
    (if (annotations.isEmpty) "" else annotations + " ") + modifiers + keyword + (if (isAnonymous) "" else name + spaceAfter(name) + ": ") + byName + tpe + repeated + default
  }

  private def textOf(annotation: ScAnnotation): String =
    textOf(annotation, emptyParens = false)

  private def textOf(annotation: ScAnnotation, emptyParens: Boolean): String = {
    val invocation = annotation.constructorInvocation
    val prefix = invocation.simpleTypeElement.map(e => textOf(e.`type`())).getOrElse("")
    val args = invocation.arguments.map(_.exprs.map(e => textWithQualifiers(e).replaceAll("\r?\n\\s*", " ")).mkString(", ")).map("(" + _ + ")").mkString
    "@" + prefix + (if (!emptyParens && args == "()") "" else args)
  }

  private def textWithQualifiers(expr: ScExpression): String =
    expr.getText // TODO Use AST
      .replaceAll("""(?<!\.|\w)Array\(""", "scala.Array(")
      .replaceAll("""([a-z]+)(?<! n|cat|origin|msg|explain|serialCommandExec)=(?=\S)""", "$1 = ")
      .replace("\"\"\"", "\"")

  private def textOf(ml: ScModifierList): String = {
    def scope = ml.getParent match {
      case Parent(p: ScPackaging) => p.packageName.split('.').lastOption.getOrElse("")
      case Parent(c: ScNamedElement) => c.name
      case _ => ""
    }
    def qualifier = ml.accessModifier.flatMap(m => if (m.isThis) Some("this") else m.idText).filter(q => !normalize || q != scope).map("[" + _ + "]").getOrElse("")
    (if (ml.isAbstract && ml.isOverride) "abstract " else "") +
      (if (ml.isOverride) "override " else "") +
      (if (ml.isPrivate) "private" + qualifier + " " else "") +
      (if (ml.isProtected) "protected" + qualifier + " " else "") +
      (if (ml.isImplicit) "implicit " else "") +
      (if (ml.isFinal) "final " else "") +
      (if (ml.isSealed) "sealed " else "") +
      (if (ml.isOpen) "open " else "") +
      (if (ml.isAbstract && !ml.isOverride) "abstract " else "") +
      (if (ml.isLazy) "lazy " else "") +
      (if (ml.isTransparent) "transparent " else "") +
      (if (ml.isOpaque) "opaque " else "") +
      (if (ml.isInline) "inline " else "") +
      (if (ml.isCase) "case " else "")
  }

  private def textOf(tpe: ScType, parens: Int = 0): String = tpe match {
    case FunctionType(_, _) if !tpe.isAliasType && parens > 0 => "(" + tpe.canonicalText + ")"
//    case AliasType(ta, _, _) => ta.containingClass.name + ".this." + ta.name
    case _ =>
      tpe.canonicalText(context).replace("_root_.", "")
  }

  private val context = TypePresentationContext.emptyContextIn(isScala3)

  private def textOf(tr: TypeResult): String = tr match {
    case Left(f) => f.toString
    case Right(t) => textOf(t)
  }

  // Should be part of .name, SCL-21919
  private def normalized(name: String): String = if (normalize && name.startsWith("`")) {
    val unquoted = name.stripPrefix("`").stripSuffix("`")
    if (!Keywords(unquoted) && isIdentifier(unquoted)) unquoted
    else name
  } else {
    name
  }

  private def spaceAfter(name: String): String =
    if (name.lastOption.exists(c => !c.isLetterOrDigit && c != '`')) " " else ""
}

private object ClassPrinter {
  private val Keywords = Set(
    ":", "=", "=>", "=>>", "?=>", "<-", "<:", "<%", ">:", "#", "@", "abstract", "case", "catch", "class", "def", "do", "else", "enum", "export", "extends", "extension",
    "false", "final", "finally", "for", "forSome", "given", "if", "implicit", "import", "lazy", "macro", "match", "new", "null", "object", "override", "package",
    "private", "protected", "return", "sealed", "super", "then", "this", "throw", "trait", "true", "try", "type", "val", "var", "while", "with", "yield",
  )

  private def isIdentifier(s: String): Boolean = s.nonEmpty && {
    if (ScalaNamesUtil.isIdentifierStart(s(0))) {
      val lastIdCharIdx = s.takeWhile(ScalaNamesUtil.isIdentifierPart).length - 1
      if (lastIdCharIdx < 0 || lastIdCharIdx == s.length - 1) true
      else if (s.charAt(lastIdCharIdx) != '_') false
      else s.drop(lastIdCharIdx + 1).forall(ScalaNamesUtil.isOpCharacter)
    } else if (ScalaNamesUtil.isOpCharacter(s(0))) {
      s.forall(ScalaNamesUtil.isOpCharacter)
    } else {
      false
    }
  }
}

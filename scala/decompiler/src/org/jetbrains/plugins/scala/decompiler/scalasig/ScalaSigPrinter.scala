/*     ___ ____ ___   __   ___   ___
**    / _// __// _ | / /  / _ | / _ \  Scala classfile decoder
**  __\ \/ /__/ __ |/ /__/ __ |/ ___/  (c) 2003-2010, LAMP/EPFL
** /____/\___/_/ |_/____/_/ |_/_/      http://scala-lang.org/
**
*/


package org.jetbrains.plugins.scala.decompiler.scalasig

import java.lang.StringBuilder
import java.util.regex.Pattern

import org.apache.commons.lang.{StringEscapeUtils, StringUtils}

import scala.annotation.{switch, tailrec}
import scala.collection.mutable
import scala.reflect.NameTransformer

//This class is from scalap, refactored to work with new types
class ScalaSigPrinter(builder: StringBuilder) {
  import ScalaSigPrinter._

  def print(s: String): Unit = builder.append(s)

  def result: String = builder.toString

  private var visitedTypeBoundsType: Set[TypeBoundsType] = Set.empty

  private val currentTypeParameters: mutable.HashMap[Symbol, String] = new mutable.HashMap[Symbol, String]()

  private def addTypeParameter(t: Symbol): Unit = {
    def checkName(name: String): Boolean = {
      currentTypeParameters.forall {
        case (_: Symbol, symbolName: String) => name != symbolName
      }
    }
    if (checkName(t.name)) {
      currentTypeParameters += ((t, t.name))
    } else {
      @tailrec
      def writeWithIndex(index: Int): Unit = {
        val nameWithIndex: String = s"${t.name}_$$_$index"
        if (checkName(nameWithIndex)) {
          currentTypeParameters += ((t, nameWithIndex))
        } else writeWithIndex(index + 1)
      }
      writeWithIndex(1)
    }
  }

  private def removeTypeParameter(t: Symbol): Unit = {
    currentTypeParameters.remove(t)
  }

  val CONSTRUCTOR_NAME = "<init>"

  val INIT_NAME = "$init$"

  case class TypeFlags(printRep: Boolean)
  implicit object _tf extends TypeFlags(false)

  def printSymbol(symbol: Symbol): Unit = {printSymbol(0, symbol)}

  def printSymbolAttributes(s: Symbol, onNewLine: Boolean, indent: => Unit): Unit = s match {
    case t: SymbolInfoSymbol =>
      for (a <- t.attributes) {
        indent; print(toString(a))
        if (onNewLine) print("\n") else print(" ")
      }
    case _ =>
  }

  private def symbolAttributes(s: Symbol): String = s match {
    case t: SymbolInfoSymbol =>
      val s = t.attributes.map(toString).mkString(" ")
      if (s.nonEmpty) s + " " else s
    case _ => ""
  }

  def printSymbol(level: Int, symbol: Symbol): Unit = {
    def isSynthetic: Boolean = symbol.isSynthetic || symbol.isCaseAccessor || symbol.isParamAccessor

    val accessibilityOk = symbol match {
      case _ if level == 0 => true
      case alias: AliasSymbol if alias.isPrivate => alias.symbolInfo.info.get match {
        case TypeRefType(_, symbol, _) => !symbol.isPrivate
        case PolyType(Ref(TypeRefType(_, symbol, _)), _) => !symbol.isPrivate
        case _ => true
      }
      case o: ObjectSymbol if o.isPrivate =>
        symbol.parent.exists(_.children.exists(s => s.isType && !s.isPrivate && s.name == o.name))
      case _               => !symbol.isPrivate
    }

    def indent(): Unit = {for (_ <- 1 to level) print("  ")}

    if (accessibilityOk && !isSynthetic) {
      symbol match {
        case o: ObjectSymbol =>
          printSymbolAttributes(o, onNewLine = true, indent())
          indent()
          if (o.name == "package" || o.name == "`package`") {
            // print package object
            printPackageObject(level, o)
          } else {
            printObject(level, o)
          }
        case c: ClassSymbol if !refinementClass(c) && !c.isModule =>
          printSymbolAttributes(c, onNewLine = true, indent())
          printClass(level, c, indent _)
        case m: MethodSymbol =>
          printSymbolAttributes(m, onNewLine = true, indent())
          printMethod(level, m, indent _)
        case a: AliasSymbol =>
          printSymbolAttributes(a, onNewLine = true, indent())
          indent()
          printAlias(level, a)
        case t: TypeSymbol if !t.isParam && !t.name.matches("_\\$\\d+") &&
          !t.name.matches("\\?(\\d)+") =>
          // todo: type 0? found in Suite class from scalatest package. So this is quickfix,
          // todo: we need to find why such strange type is here
          printSymbolAttributes(t, onNewLine = true, indent())
          indent()
          printTypeSymbol(level, t)
        case _ =>
      }
    }
  }

  def isCaseClassObject(o: ObjectSymbol): Boolean = {
    val TypeRefType(_, Ref(classSymbol: ClassSymbol), _) = o.infoType
    o.isFinal && (classSymbol.children.find(x => x.isCase && x.isInstanceOf[MethodSymbol]) match {
      case Some(_) => true
      case None => false
    })
  }

  private def underObject(m: MethodSymbol) = m.parent match {
    case Some(c: ClassSymbol) => c.isModule
    case _ => false
  }

  private def underTrait(m: MethodSymbol) = m.parent match {
    case Some(c: ClassSymbol) => c.isTrait
    case _ => false
  }


  private def printChildren(level: Int, symbol: Symbol, filterFirstCons: Boolean = false): Unit = {
    val previousLength = builder.length

    var firstConsFiltered = !filterFirstCons
    for (child <- symbol.children) {
      if (child.isParam && child.isType) {} // do nothing
      else if (!firstConsFiltered)
        child match {
          case m: MethodSymbol if m.name == CONSTRUCTOR_NAME => firstConsFiltered = true
          case _ =>
            val previousLength = builder.length
            printSymbol(level + 1, child)
            if (builder.length > previousLength) print("\n")
        }
      else {
        val previousLength = builder.length
        printSymbol(level + 1, child)
        if (builder.length > previousLength) print("\n")
      }
    }

    if (builder.length > previousLength) {
      builder.delete(builder.length - 1, builder.length)
    }
  }

  def printWithIndent(level: Int, s: String): Unit = {
    def indent(): Unit = {for (i <- 1 to level) print("  ")}
    indent()
    print(s)
  }

  def printModifiers(symbol: Symbol): Unit = {
    lazy val privateWithin: Option[String] = {
      symbol match {
        case sym: SymbolInfoSymbol => sym.symbolInfo.privateWithin match {
          case Some(t: Ref[Symbol]) => Some("[" + processName(t.get.name) + "]")
          case _ => None
        }
        case _ => None
      }
    }

    symbol.parent match {
      case Some(cSymbol: ClassSymbol) if refinementClass(cSymbol) => return //no modifiers allowed inside refinements
      case _ =>
    }

    if (symbol.isAbstractOverride) print("abstract override ")
    if (symbol.isOverride) print("override ")
    // print private access modifier
    if (symbol.isPrivate) {
      print("private")
      if (symbol.isLocal) print("[this] ")
      else print(" ")
    }
    else if (symbol.isProtected) {
      print("protected")
      if (symbol.isLocal) print("[this]")
      else privateWithin foreach print
      print(" ")
    }
    else privateWithin.foreach(s => print("private" + s + " "))

    if (symbol.isSealed) print("sealed ")
    if (symbol.isImplicit) print("implicit ")
    if (symbol.isFinal && !symbol.isInstanceOf[ObjectSymbol]) print("final ")
    if (symbol.isAbstract) symbol match {
      case c@(_: ClassSymbol | _: ObjectSymbol) if !c.isTrait => print("abstract ")
      case _ => ()
    }
    if (symbol.isCase && !symbol.isMethod) print("case ")
  }

  private def refinementClass(c: ClassSymbol) = c.name == "<refinement>"

  def printClass(level: Int, c: ClassSymbol, indent: () => Unit = () => ()): Unit = {
    if (c.name == "<local child>" /*scala.tools.nsc.symtab.StdNames.LOCALCHILD.toString()*/ ) {
      // Skip
    } else if (c.name == "<refinement>") {
      indent()
      print(" { ")
      val previousLength = builder.length
      printChildren(level, c)
      builder.replace(previousLength, builder.length, LineSeparator.replaceAllIn(builder.substring(previousLength, builder.length).trim, "; "))
      print(" }")
    } else {
      indent()
      printModifiers(c)
      val (contextBounds, defaultConstructor) = if (!c.isTrait) getPrinterByConstructor(c) else (Seq.empty, "")
      if (c.isTrait) print("trait ") else print("class ")
      print(processName(c.name))
      val it = c.infoType
      val cons =
        if (c.isCase) defaultConstructor
        else if (defaultConstructor.startsWith("()")) defaultConstructor.substring(2)
        else if (defaultConstructor.startsWith(" private ()") && defaultConstructor.length > 11) " private " + defaultConstructor.substring(11)
        else if (defaultConstructor.startsWith(" protected ()") && defaultConstructor.length > 13) " protected " + defaultConstructor.substring(13)
        else defaultConstructor
      val (classType, typeParams) = it match {
        case PolyType(typeRef, symbols) => (PolyTypeWithCons(typeRef, symbols, cons, contextBounds), symbols)
        case ClassInfoType(a, b) if !c.isTrait => (ClassInfoTypeWithCons(a, b, cons), Seq.empty)
        case _ => (it, Seq.empty)
      }
      for (param <- typeParams) addTypeParameter(param.get)
      printType(classType)
      try {
        val previousLength = builder.length
        print(" {")
        //Print class selftype
        val selfType = c.thisTypeRef match {
          case Some(t) => t.get match {
            case RefinedType(_, Seq(_, t2)) => Some(t2)
            case _ => Some(t)
          }
          case None => None
        }
        selfType match {
          case Some(t) => print(" this: " + toString(t.get, "", parens = 1) + " =>")
          case None =>
        }
        print("\n")
        printChildren(level, c, !c.isTrait)
        if (builder.length == previousLength + 3) {
          builder.delete(previousLength, previousLength + 2)
        } else {
          printWithIndent(level, "}\n")
        }
      }
      finally {
        for (param <- typeParams) removeTypeParameter(param.get)
      }
    }
  }

  private val LineSeparator = "\n\\s*".r

  def getClassString(level: Int, c: ClassSymbol): String = {
    val printer = new ScalaSigPrinter(new StringBuilder())
    printer.printClass(level, c)
    printer.result
  }

  def getPrinterByConstructor(c: ClassSymbol): (Seq[(String, String)], String) = {
    c.children.find {
      case m: MethodSymbol if m.name == CONSTRUCTOR_NAME => true
      case _ => false
    } match {
      case Some(m: MethodSymbol) =>
        val printer = new ScalaSigPrinter(new StringBuilder())
        printer.printPrimaryConstructor(m, c)
        val res = printer.result
        (if (!m.isPrivate) contextBoundsIn(m.infoType) else Seq.empty, if (res.length() > 0 && res.charAt(0) != '(') " " + res else res)
      case _ => (Seq.empty, "")
    }
  }

  def printPrimaryConstructor(m: MethodSymbol, c: ClassSymbol): Unit = {
    printSymbolAttributes(m, onNewLine = false, ())
    printModifiers(m)
    printMethodType(m.infoType, printResult = false, methodSymbolAsClassParam(_, c, m))(())
  }

  def printPackageObject(level: Int, o: ObjectSymbol): Unit = {
    printModifiers(o)
    print("package ")
    print("object ")
    val poName = o.symbolInfo.owner.get.name
    print(processName(poName))
    val TypeRefType(_, Ref(classSymbol: ClassSymbol), _) = o.infoType
    printType(classSymbol)
    val previousLength = builder.length
    print(" {\n")
    printChildren(level, classSymbol)
    if (builder.length == previousLength + 3) {
      builder.delete(previousLength, previousLength + 2)
    } else {
      printWithIndent(level, "}\n")
    }
  }

  def printObject(level: Int, o: ObjectSymbol): Unit = {
    printModifiers(o)
    print("object ")
    print(processName(o.name))
    val TypeRefType(_, Ref(classSymbol: ClassSymbol), _) = o.infoType
    printType(classSymbol)
    val previousLength = builder.length
    print(" {\n")
    printChildren(level, classSymbol)
    if (builder.length == previousLength + 3) {
      builder.delete(previousLength, previousLength + 2)
    } else {
      printWithIndent(level, "}\n")
    }
  }

  private def methodSymbolAsMethodParam(ms: MethodSymbol): String = {
    val nameId = processName(ms.name)
    val nameAndType = nameId + colonAfter(nameId)+ toString(ms.infoType)(TypeFlags(true))
    val default = if (ms.hasDefault) compiledCodeBody else ""
    nameAndType + default
  }

  private def methodSymbolAsClassParam(msymb: MethodSymbol, c: ClassSymbol, primaryConstructor: MethodSymbol) = {
    val sb = new StringBuilder()
    val printer = new ScalaSigPrinter(sb)
    val methodName = msymb.name
    val paramAccessors = c.children.filter {
      case ms: MethodSymbol if ms.isParamAccessor && ms.name.startsWith(methodName) => true
      case _ => false
    }
    val isMutable = paramAccessors.exists(acc => isSetterFor(acc.name, methodName))
    val toPrint = paramAccessors.find(m => !m.isPrivate || !m.isLocal)
    if (!primaryConstructor.isPrivate || toPrint.exists(m => !m.isLocal && !m.isPrivate)) {
      toPrint match {
        case Some(ms) =>
          if (!ms.isPrivate) {
            val previousLength = sb.length
            printer.printModifiers(ms)
            if (isMutable) printer.print("var ")
            else if (!(c.isCase && sb.length == previousLength)) printer.print("val ")
          }
        case _ =>
      }

      val nameId = processName(methodName)
      val nameAndType = nameId + colonAfter(nameId) + toString(msymb.infoType)(TypeFlags(true))
      val default = if (msymb.hasDefault) compiledCodeBody else ""
      printer.print(nameAndType + default)
    }
    printer.result
  }

  def printMethodType(t: Type, printResult: Boolean,
                      pe: MethodSymbol => String = methodSymbolAsMethodParam,
                      needsSpace: Boolean = false)(cont: => Unit): Unit = {

    def _pmt(mt: FunctionType): Unit = {

      val isImplicitClause = isImplicit(mt)

      val paramSymbolsWithoutContextBounds =
        if (isImplicitClause) mt.paramSymbols.dropWhile(ps => ps.name.startsWith("evidence$") && hasSingleArgument(ps))
        else mt.paramSymbols

      if (!isImplicitClause || paramSymbolsWithoutContextBounds.nonEmpty) {
        val isImplicit = mt match {
          case _: ImplicitMethodType => true
          //for Scala 2.9
          case mt: MethodType if mt.paramSymbols.nonEmpty && mt.paramSymbols.head.isImplicit => true
          case _ => false
        }
        val paramEntries = paramSymbolsWithoutContextBounds.map({
          case ms: MethodSymbol =>
            val s = pe(ms)
            symbolAttributes(ms) + (if (isImplicit) s.replace("implicit ", "") else s)
          case _ => "^___^"
        })
        // Print parameter clauses
        val entries = paramEntries.filter(_.nonEmpty)
        print(entries.mkString(if (isImplicit && entries.nonEmpty) "(implicit " else "(", ", ", ")"))
      }

      // Print result type
      mt.resultType.get match {
        case mt: MethodType => printMethodType(mt, printResult, pe)({})
        case imt: ImplicitMethodType => printMethodType(imt, printResult, pe)({})
        case x => if (printResult) {
          print(": ")
          printType(x)
        }
      }
    }

    t match {
      case mt@MethodType(_, _) => _pmt(mt)
      case mt@ImplicitMethodType(_, _) => _pmt(mt)
      case pt: PolyType =>
        val typeParams = pt.paramSymbols
        for (param <- typeParams) addTypeParameter(param)
        print(typeParamString(typeParams, contextBoundsIn(pt.typeRef.get)))
        try {
          printMethodType(pt.typeRef.get, printResult)({})
        }
        finally {
          for (param <- typeParams) removeTypeParameter(param)
        }
      //todo consider another method types
      case x =>
        if (needsSpace) print(" ")
        print(": "); printType(x)
    }

    // Print rest of the symbol output
    cont
  }

  private def contextBoundsIn(t: Type): Seq[(String, String)] = t match {
    case mt: FunctionType => // TODO Unnecessary if NullaryMethodType is FunctionType
      val implicitClause = implicitClauseIn(mt)
      val contextBoundParams = implicitClause.map(_.paramSymbols.takeWhile(ps => ps.name.startsWith("evidence$") && hasSingleArgument(ps))).getOrElse(Seq.empty)
      contextBoundParams.collect { case ms: MethodSymbol =>
        val TypeRefType(prefix, symbol, Seq(argument)) = ms.infoType
        (toString(argument)(TypeFlags(true)), toString(TypeRefType(prefix, symbol, Seq()))(TypeFlags(true)))
      }
    case _ => Seq.empty
  }

  @tailrec
  private def implicitClauseIn(mt: FunctionType): Option[FunctionType] =
    if (isImplicit(mt)) Some(mt) else mt.resultType.get match {
      case mt: FunctionType => implicitClauseIn(mt)
      case _ => None
    }

  private def isImplicit(mt: FunctionType): Boolean =
    mt.isInstanceOf[ImplicitMethodType] || mt.paramSymbols.headOption.exists(_.isImplicit)

  private def hasSingleArgument(ps: Symbol): Boolean = ps match {
    case ms: MethodSymbol => ms.infoType match {
      case TypeRefType(_, _, Seq(_)) => true
      case _ => false
    }
    case _ => false
  }

  def printMethod(level: Int, m: MethodSymbol, indent: () => Unit): Unit = {
    val n = m.name
    if (underObject(m) && n == CONSTRUCTOR_NAME) return
    if (underTrait(m) && n == INIT_NAME) return
    if (n.isDefaultParameterMethodName) return // skip default function parameters
    if (n.startsWith("super$")) return // do not print auxiliary qualified super accessors
    if (m.isAccessor && n.endsWith(setterSuffix)) return
    if (m.isParamAccessor) return //do not print class parameters
    if (n.startsWith("<local ")) return //isLocalDummy whatever, see scala.reflect.internal.StdNames.TermNames.LOCALDUMMY_PREFIX
    indent()
    printModifiers(m)

    def hasSetter: Boolean = m.parent.get.children.exists {
      case ms: MethodSymbol => isSetterFor(ms.name, m.name)
      case _ => false
    }

    val keywords =
      if (!m.isAccessor) "def "
      else if (m.isLazy) "lazy val "
      else if (hasSetter) "var "
      else "val "

    print(keywords)

    n match {
      case CONSTRUCTOR_NAME =>
        print("this")
        printMethodType(m.infoType, printResult = false) {
          print(compiledCodeBody)
        }
      case name =>
        val nn = processName(name)
        print(nn)

        val isConstantValueDefinition = m.isFinal && keywords.startsWith("val")

        m.infoType match {
          case isConstantType(ct) if isConstantValueDefinition =>
            Constants.constantExpression(ct) match {
              case Some(expr) => print(s" = $expr")
              case None =>
                if (needsSpace(nn)) print(" ")
                print(s": ${Constants.typeText(ct)} $compiledCodeBody")
            }
          case _                                               =>
            val printBody = !m.isDeferred && (m.parent match {
              case Some(c: ClassSymbol) if refinementClass(c) => false
              case _ => true
            })
            printMethodType(m.infoType, printResult = true, needsSpace = needsSpace(nn))(
              {if (printBody) print(compiledCodeBody /* Print body only for non-abstract methods */ )}
            )
        }
    }
    print("\n")
  }

  def printAlias(level: Int, a: AliasSymbol): Unit = {
    printModifiers(a)
    print("type ")
    print(processName(a.name))
    val tp: Unit = a.infoType match {
      case PolyType(typeRef, symbols) => printType(PolyTypeWithCons(typeRef, symbols, " = "))
      case t => printType(t, " = ")
    }
    print("\n")
    printChildren(level, a)
  }

  def printTypeSymbol(level: Int, t: TypeSymbol): Unit = {
    printModifiers(t)
    print("type ")
    print(processName(t.name))
    t.infoType match {
      case PolyType(typeRef, symbols) => printType(PolyTypeWithCons(typeRef, symbols, ""))
      case _ => printType(t.infoType)
    }
    print("\n")
  }

  def toString(attrib: SymAnnot): String = {
    val prefix = toString(attrib.typeRef, "@")
    val inScala = prefix.startsWith("@_root_.scala.")
    if (attrib.hasArgs) {
      val argTexts = attrib.args.map(annotArgText)
      val namedArgsText = attrib.namedArgs.map { case (name, value) =>
        // For some reason, positional arguments are always encoded as named arguments, even for Scala annotations.
        if (inScala) annotArgText(value) else s"${processName(name)} = ${annotArgText(value)}"
      }
      (argTexts ++ namedArgsText).mkString(s"$prefix(", ", ", ")")
    }
    else prefix
  }

  // TODO char, float, etc.
  def annotArgText(arg: Any): String = {
    arg match {
      case s: String => quote(s)
      case Name(s: String) => quote(s)
      case Constant(v) => annotArgText(v)
      case Ref(v) => annotArgText(v)
      case AnnotArgArray(args) =>
        args.map(ref => annotArgText(ref.get)).mkString("_root_.scala.Array(", ", ", ")")
      case t: Type => "classOf[%s]" format toString(t)
      case null => "null"
      case _ => arg.toString
    }
  }

  def printType(sym: SymbolInfoSymbol)(implicit flags: TypeFlags): Unit = printType(sym.infoType)(flags)

  def printType(t: Type)(implicit flags: TypeFlags): Unit = print(toString(t)(flags))

  def printType(t: Type, sep: String)(implicit flags: TypeFlags): Unit = print(toString(t, sep)(flags))

  def toString(t: Type)(implicit flags: TypeFlags): String = toString(t, "")(flags)

  def toString(t: Type, level: Int)(implicit flags: TypeFlags): String = toString(t, "", level)(flags)

  private val SingletonTypePattern = """(.*?)\.type""".r

  //TODO: this passing of 'level' look awful;
  def toString(t: Type, sep: String, level: Int = 0, parens: Int = 0)(implicit flags: TypeFlags): String = {

    // print type itself
    t match {
      case ThisType(Ref(classSymbol: ClassSymbol)) if refinementClass(classSymbol) => sep + "this.type"
      case ThisType(Ref(symbol)) => sep + processName(symbol.name) + ".this.type"
      case SingleType(Ref(ThisType(Ref(thisSymbol: ClassSymbol))), symbol) =>
        val thisSymbolName: String =
          thisSymbol.name match {
            case "package" => thisSymbol.symbolInfo.owner match {
              case Ref(ex: ExternalSymbol) => "_root_." + processName(ex.path)
              case _ => "this"
            }
            case name if thisSymbol.isModule => if (thisSymbol.isStableObject) "_root_." + processName(thisSymbol.path).stripPrefix("<empty>.") else processName(name)
            case name => processName(name) + ".this"
          }
        sep + thisSymbolName + "." + processName(symbol.name) + ".type"
      case SingleType(Ref(ThisType(Ref(exSymbol: ExternalSymbol))), symbol) if exSymbol.name == "<root>" =>
        sep + "_root_." + processName(symbol.name) + ".type"
      case SingleType(Ref(ThisType(Ref(exSymbol: ExternalSymbol))), Ref(symbol)) =>
        sep + "_root_." + processName(exSymbol.path).stripPrefix("<empty>.").removeDotPackage + "." +
          processName(symbol.name) + ".type"
      case SingleType(Ref(NoPrefixType), Ref(symbol)) =>
        sep + processName(symbol.name) + ".type"
      case SingleType(typeRef, symbol) =>
        var typeRefString = toString(typeRef, level)
        if (typeRefString.endsWith(".type")) typeRefString = typeRefString.dropRight(5)
        typeRefString = typeRefString.removeDotPackage
        sep + typeRefString + "." + processName(symbol.name) + ".type"
      case ConstantType(Ref(c)) =>
        sep + Constants.typeText(c)
      case TypeRefType(Ref(NoPrefixType), Ref(symbol: TypeSymbol), typeArgs) if currentTypeParameters.isDefinedAt(symbol) =>
        sep + processName(currentTypeParameters.getOrElse(symbol, symbol.name)) + typeArgString(typeArgs, level)
      case TypeRefType(prefix, symbol, typeArgs) => sep + (symbol.path match {
        case "scala.<repeated>" => flags match {
          case TypeFlags(true) => toString(typeArgs.head, "", level, 1) + "*"
          case _ => "_root_.scala.Seq" + typeArgString(typeArgs, level)
        }
        case "scala.<byname>" =>
          val s = "=> " + toString(typeArgs.head, level)
          if (parens > 1) "(" + s + ")" else s
        case _ =>
          def checkContainsSelf(self: Option[Type], parent: Symbol): Boolean = {
            self match {
              case Some(tp) =>
                tp match {
                  case ThisType(Ref(sym)) => sym == parent
                  case SingleType(_, Ref(sym)) => sym == parent
                  case _: ConstantType => false
                  case TypeRefType(_, Ref(sym), _) => sym == parent
                  case _: TypeBoundsType => false
                  case RefinedType(Ref(sym), refs) => sym == parent || refs.exists(tp => checkContainsSelf(Some(tp), parent))
                  case ClassInfoType(Ref(sym), refs) => sym == parent || refs.exists(tp => checkContainsSelf(Some(tp), parent))
                  case ClassInfoTypeWithCons(Ref(sym), refs, _) => sym == parent || refs.exists(tp => checkContainsSelf(Some(tp), parent))
                  case ImplicitMethodType(_, _) => false
                  case MethodType(_, _) => false
                  case NullaryMethodType(_) => false
                  case PolyType(typeRef, symbols) =>
                    checkContainsSelf(Some(typeRef), parent) || symbols.exists(_.get == parent)
                  case PolyTypeWithCons(typeRef, symbols, _, _) =>
                    checkContainsSelf(Some(typeRef), parent) || symbols.exists(_.get == parent)
                  case AnnotatedType(typeRef) => checkContainsSelf(Some(typeRef), parent)
                  case AnnotatedWithSelfType(typeRef, Ref(sym), _) =>
                    checkContainsSelf(Some(typeRef), parent) || sym == parent
                  case ExistentialType(typeRef, symbols) =>
                    checkContainsSelf(Some(typeRef), parent) || symbols.exists(_.get == parent)
                  case _ => false
                }
              case None => false
            }
          }
          val prefixStr = (prefix.get, symbol.get, toString(prefix.get, level)) match {
            case (NoPrefixType, _, _) => ""
            case (ThisType(Ref(objectSymbol)), _, _) if objectSymbol.isModule =>
              objectSymbol match {
                case classSymbol: ClassSymbol if objectSymbol.name == "package" =>
                  "_root_." + processName(classSymbol.symbolInfo.owner.path) + "."
                case _ =>
                  (if (objectSymbol.isStableObject) "_root_." + processName(objectSymbol.path) else processName(objectSymbol.name)) + "."
              }
            case (ThisType(packSymbol), _, _) if !packSymbol.isType =>
              val s = packSymbol.path.stripPrefix("<root>")
              "_root_." + (if (s.nonEmpty) processName(s) + "." else "")
            case (ThisType(Ref(classSymbol: ClassSymbol)), _, _) if refinementClass(classSymbol) => ""
            case (ThisType(Ref(typeSymbol: ClassSymbol)), ExternalSymbol(_, Some(parent), _), _)
              if typeSymbol.path != parent.path && checkContainsSelf(typeSymbol.thisTypeRef, parent) =>
              processName(typeSymbol.name) + ".this."
            case (ThisType(typeSymbol), ExternalSymbol(_, Some(parent), _), _) if typeSymbol.path != parent.path =>
              processName(typeSymbol.name) + ".super[" + processName(parent.name) + "/*"+ parent.path +"*/]."
            case (_, _, SingletonTypePattern(a)) => a + "."
            case (_, _, a) => a + "#"
          }
          //remove package object reference
          val path = prefixStr.removeDotPackage
          val name = processName(symbol.name)
          val res = path + name
          val suffix =
            if (name == "_") {
              symbol.get match {
                case ts: TypeSymbol =>
                  ts.infoType match {
                    case t: TypeBoundsType =>
                      if (visitedTypeBoundsType.contains(t)) ""
                      else {
                        visitedTypeBoundsType += t
                        try     toString(t, level)
                        finally visitedTypeBoundsType -= t
                      }
                    case _ => ""
                  }
                case _ => ""
              }
            } else symbol.get match {
                case ex: ExternalSymbol if ex.isObject => ".type"
                case _                                 => ""
              }
          val base = res.stripPrefix("_root_.<empty>.")
          val isInfix = base.nonEmpty && base.forall(!_.isLetterOrDigit) && typeArgs.length == 2
          val result = if (isInfix) {
            typeArgs.map(toString(_, "", level, 1)).mkString(" " + base + " ")
          } else if (typeArgs.nonEmpty && base.startsWith("_root_.scala.Tuple") && base != "_root_.scala.Tuple1" && !base.substring(18).contains(".")) {
            val s = typeArgs.map(toString(_, level)).mkString("(", ", ", ")")
            if (parens > 1) "(" + s + ")" else s
          } else if (typeArgs.nonEmpty && base.startsWith("_root_.scala.Function")) {
            val params = if (typeArgs.length == 2) toString(typeArgs.head, "", level, 2) else typeArgs.init.map(toString(_, level)).mkString("(", ", ", ")")
            val s = params + " => " + toString(typeArgs.last, level)
            if (parens > 0) "(" + s + ")" else s
          } else {
            base + typeArgString(typeArgs, level)
          }
          result + suffix
      })
      case TypeBoundsType(lower, upper) =>
        val lb = toString(lower, level)
        val ub = toString(upper, level)
        val lbs = if (!lb.equals("_root_.scala.Nothing")) " >: " + lb else ""
        val ubs = if (!ub.equals("_root_.scala.Any")) " <: " + ub else ""
        lbs + ubs
      case RefinedType(Ref(classSym: ClassSymbol), typeRefs) =>
        val classStr = {
          val text = getClassString(level + 1, classSym)
          if (text.trim.stripPrefix("{").stripSuffix("}").trim.isEmpty) ""
          else text
        }
        val parents = {
          val parents0 = typeRefs.map(toString(_, "", level, 1))
          if (classStr.nonEmpty && parents0 == Seq("_root_.scala.AnyRef")) "" else parents0.mkString("", " with ", "")
        }
        if (parents.nonEmpty) sep + parents + classStr else sep + classStr.stripPrefix(" ")
      case RefinedType(_, typeRefs) => sep + typeRefs.map(toString(_, "", level, 1)).mkString("", " with ", "")
      case ClassInfoType(symbol, typeRefs) =>
        val parents = simplify(symbol, typeRefs.map(toString(_, "", level, 1)))
        if (parents.nonEmpty) sep + parents.mkString(" extends ", " with ", "") else sep
      case ClassInfoTypeWithCons(symbol, typeRefs, cons) =>
        val parents = simplify(symbol, typeRefs.map(toString(_, "", level, 1)))
        if (parents.nonEmpty) sep + parents.mkString(cons + " extends ", " with ", "") else sep + cons

      case ImplicitMethodType(resultType, _) => toString(resultType, sep, level)
      case MethodType(resultType, _) => toString(resultType, sep, level)
      case NullaryMethodType(resultType) => toString(resultType, sep, level)

      case PolyType(typeRef, symbols) =>
        "({ type λ" + typeParamString(symbols, contextBoundsIn(typeRef.get)) + " = " + toString(typeRef, sep, level) + " })#λ"
      case PolyTypeWithCons(typeRef, symbols, cons, contextBounds) =>
        typeParamString(symbols, contextBounds) + cons + toString(typeRef, sep, level)
      case AnnotatedType(typeRef) =>
        toString(typeRef, sep, level)
      case AnnotatedWithSelfType(typeRef, _, _) => toString(typeRef, sep, level)
      //case DeBruijnIndexType(typeLevel, typeIndex) =>
      case ExistentialType(typeRef, symbols) =>
        val refs = symbols.map(_.get).map(toString).filter(!_.startsWith("_")).map("type " + _).distinct
        toString(typeRef, sep, level) + (if (refs.nonEmpty) refs.mkString(" forSome {", "; ", "}") else "")
      case _ => sep + t.toString
    }
  }

  private def simplify(symbol: Symbol, parents: Seq[String]): Seq[String] = {
    val parents0 = parents.dropWhile(p => p == "_root_.scala.AnyRef" || p == "_root_.java.lang.Object")
    val parents1 = if (symbol.isCase) parents0.filterNot(_ == "_root_.scala.Product").filterNot(_ == "_root_.scala.Serializable") else parents0
    if (symbol.isModule) parents1.filterNot(_ == "_root_.java.io.Serializable") else parents1
  }

  def getVariance(t: TypeSymbol): String = if (t.isCovariant) "+" else if (t.isContravariant) "-" else ""

  def toString(symbol: Symbol): String = symbol match {
    case symbol: TypeSymbol =>
      val attrs = (for (a <- symbol.attributes) yield toString(a)).mkString(" ")
      val atrs = if (attrs.nonEmpty) attrs.trim + " " else ""
      val symbolType = symbol.infoType match {
        case PolyType(typeRef, symbols) => PolyTypeWithCons(typeRef, symbols, "")
        case tp => tp
      }
      val name: String = currentTypeParameters.getOrElse(symbol, symbol.name)
      atrs + getVariance(symbol) + processName(name) + toString(symbolType)
    case _ => symbol.toString
  }

  def typeArgString(typeArgs: Seq[Type], level: Int): String =
    if (typeArgs.isEmpty) ""
    else typeArgs.map(toString(_, level)).map(_.stripPrefix("=> ")).mkString("[", ", ", "]")

  def typeParamString(params: Seq[Symbol], bounds: Seq[(String, String)] = Seq.empty): String =
    if (params.isEmpty) ""
    else params.map { param =>
      val contextBounds = bounds.map { case (id, tpe) =>
        val nameId = processName(currentTypeParameters.getOrElse(param, param.name))
        if (id == nameId) colonAfter(nameId) + tpe else ""
      }
      toString(param) + contextBounds.mkString
    }.mkString("[", ", ", "]")

  private object isConstantType {
    @tailrec
    def unapply(arg: Type): Option[Constant] = arg match {
      case ConstantType(Ref(c)) => Some(c)
      case NullaryMethodType(Ref(tpe)) => unapply(tpe)
      case _ => None
    }
  }

  private object Constants {
    private def classTypeText(typeRef: TypeRefType): String = {
      val ref = typeRef.symbol.get.path
      val args = typeRef.typeArgs

      ref + typeArgString(args, 0)
    }

    def typeText(ct: Constant): String =
      (nonLiteralTypeText orElse
        literalText orElse
        symbolLiteralText).apply(ct.value)

    def constantExpression(ct: Constant): Option[String] =
      (constantDefinitionExpr orElse literalText).lift(ct.value)

    private val nonLiteralTypeText: PartialFunction[Any, String] = {
      case null                                         => "_root_.scala.Null"
      case _: Unit                                      => "_root_.scala.Unit"
      case _: Short                                     => "_root_.scala.Short" //there are no literals for shorts and bytes
      case _: Byte                                      => "_root_.scala.Byte"
      case Ref(typeRef: TypeRefType)                    => s"_root_.java.lang.Class[${classTypeText(typeRef)}]"
      case Ref(ExternalSymbol(_, Some(Ref(parent)), _)) => parent.path  //enum type
    }

    //symbol literals are not valid constant expression
    private val symbolLiteralText: PartialFunction[Any, String] = {
      case Ref(ScalaSymbol(value)) => "\'" + value
    }

    private val constantDefinitionExpr: PartialFunction[Any, String] = {
      case Ref(sym: ExternalSymbol)  => sym.path //enum value
      case Ref(typeRef: TypeRefType) => s"_root_.scala.Predef.classOf[${classTypeText(typeRef)}]" //class literal

      // java numeric constants with special `toString`
      // Double and Float infinities are equal, so we should check type first
      case d: Double if d == java.lang.Double.POSITIVE_INFINITY => "_root_.java.lang.Double.POSITIVE_INFINITY"
      case d: Double if d == java.lang.Double.NEGATIVE_INFINITY => "_root_.java.lang.Double.NEGATIVE_INFINITY"

      case f: Float if f == java.lang.Float.POSITIVE_INFINITY => "_root_.java.lang.Float.POSITIVE_INFINITY"
      case f: Float if f == java.lang.Float.NEGATIVE_INFINITY => "_root_.java.lang.Float.NEGATIVE_INFINITY"

      // NaNs cannot be compared directly
      case d: Double if java.lang.Double.isNaN(d) => "_root_.java.lang.Double.NaN"
      case f: Float  if java.lang.Float.isNaN(f)  => "_root_.java.lang.Float.NaN"
    }

    private val literalText: PartialFunction[Any, String] = {
      case null                                    => "null"
      case value: String                           => quote(value, canUseMultiline = false)
      case Ref(Name(value))                        => quote(value, canUseMultiline = false)
      case value: Char                             => "\'" + value + "\'"
      case value: Long                             => value.toString + "L"
      case value: Float                            => value.toString + "F"
      case value: Double                            => value.toString + "D"
      case value@(_: Boolean | _: Int) => value.toString
    }
  }
}

object ScalaSigPrinter {
  val keywordList =
    Set("true", "false", "null", "abstract", "case", "catch", "class", "def",
      "do", "else", "extends", "final", "finally", "for", "forSome", "if",
      "implicit", "import", "lazy", "match", "new", "object", "override",
      "package", "private", "protected", "return", "sealed", "super",
      "this", "throw", "trait", "try", "type", "val", "var", "while", "with",
      "yield")

  val compiledCodeBody = " = ???"

  //name may be qualified here
  def processName(name: String): String = {
    val parts = name.stripPrivatePrefix.split('.')
    var idx = 0
    while (idx < parts.length) {
      parts(idx) = processSimpleName(parts(idx)) //no need to create intermediate array here
      idx += 1
    }
    parts.mkString(".")
  }

  private def processSimpleName(name: String): String = {
    name
      .decode
      .fixPlaceholderNames
      .fixExistentialTypeParamName
      .escapeNonIdentifiers
  }

  private def isSetterFor(setterName: String, methodName: String) = {
    val correctLength = setterName.length == methodName.length + setterSuffix.length
    correctLength && setterName.startsWith(methodName) && setterName.endsWith(setterSuffix)
  }

  private val setterSuffix = "_$eq"

  private val placeholderPattern = Pattern.compile("_\\$\\S?\\d+")

  private val defaultParamMarker = "$default$"

  private implicit class StringFixes(private val str: String) extends AnyVal {
    def decode: String = NameTransformer.decode(str)

    //noinspection MutatorLikeMethodIsParameterless
    def removeDotPackage: String = StringUtils.replace(str, ".`package`", "")

    def stripPrivatePrefix: String = if (placeholderPattern.matcher(str).matches) str else {
      val i = str.lastIndexOf("$$")
      if (i > 0) str.substring(i + 2) else str
    }

    //to avoid names like this one: ?0 (from existential type parameters)
    def fixExistentialTypeParamName: String = {
      if (str.length() > 1 && str(0) == '?' && str(1).isDigit) "x" + str.substring(1)
      else str
    }

    def fixPlaceholderNames: String = {
      if (str.indexOf('_') < 0) str //optimization
      else placeholderPattern.matcher(str).replaceAll("_")
    }

    def escapeNonIdentifiers: String = {
      if (str == "<empty>") str
      else if (!isIdentifier(str) || keywordList.contains(str) || str == "=") "`" + str + "`"
      else str
    }

    def isDefaultParameterMethodName: Boolean = {
      val idx = str.indexOf(defaultParamMarker)
      val afterMarker = idx + defaultParamMarker.length

      idx > 0 && str.length > afterMarker && str.charAt(afterMarker).isDigit
    }
  }

  private def isIdentifier(id: String): Boolean = {
    //following four methods is the same like in scala.tools.nsc.util.Chars class
    /** Can character start an alphanumeric Scala identifier? */
    def isIdentifierStart(c: Char): Boolean =
      (c == '_') || (c == '$') || Character.isUnicodeIdentifierStart(c)

    /** Can character form part of an alphanumeric Scala identifier? */
    def isIdentifierPart(c: Char) =
      (c == '$') || Character.isUnicodeIdentifierPart(c)

    /** Is character a math or other symbol in Unicode?  */
    def isSpecial(c: Char) = {
      val chtp = Character.getType(c)
      chtp == Character.MATH_SYMBOL.toInt || chtp == Character.OTHER_SYMBOL.toInt
    }

    /** Can character form part of a Scala operator name? */
    def isOperatorPart(c : Char) : Boolean = (c: @switch) match {
      case '~' | '!' | '@' | '#' | '%' |
           '^' | '*' | '+' | '-' | '<' |
           '>' | '?' | ':' | '=' | '&' |
           '|' | '/' | '\\' => true
      case _ => isSpecial(c)
    }

    def hasCommentStart(s: String) = s.contains("//") || s.contains("/*")

    def lastIdentifierCharIdx(s: String): Int = {
      var idx = -1
      while (idx + 1 < s.length && isIdentifierPart(s.charAt(idx + 1))) {
        idx += 1
      }
      idx
    }

    if (id.isEmpty || hasCommentStart(id)) return false

    if (isIdentifierStart(id(0))) {
      val lastIdCharIdx = lastIdentifierCharIdx(id)

      if (lastIdCharIdx < 0 || lastIdCharIdx == id.length - 1) //simple id
        true
      else if (id.charAt(lastIdCharIdx) != '_')
        false
      else
        id.drop(lastIdCharIdx + 1).forall(isOperatorPart)

    } else if (isOperatorPart(id(0))) {
      id.forall(isOperatorPart)
    } else false
  }

  def quote(s: String, canUseMultiline: Boolean = true): String =
    if (canUseMultiline && (s.contains("\n") || s.contains("\r"))) "\"\"\"" + s + "\"\"\""
    else "\"" +  StringEscapeUtils.escapeJava(s) + "\""

  private def colonAfter(id: String) = if (needsSpace(id)) " : " else ": "

  private def needsSpace(id: String) = id.lastOption.exists(c => !c.isLetterOrDigit && c != '`')
}
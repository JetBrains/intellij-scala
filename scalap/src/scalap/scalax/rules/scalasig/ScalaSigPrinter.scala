/*     ___ ____ ___   __   ___   ___
**    / _// __// _ | / /  / _ | / _ \  Scala classfile decoder
**  __\ \/ /__/ __ |/ /__/ __ |/ ___/  (c) 2003-2010, LAMP/EPFL
** /____/\___/_/ |_/____/_/ |_/_/      http://scala-lang.org/
**
*/


package scala.tools.scalap
package scalax
package rules
package scalasig

import java.io.{ByteArrayOutputStream, PrintStream}
import java.util.regex.Pattern

import scala.annotation.{switch, tailrec}
import scala.collection.mutable
import scala.reflect.NameTransformer
import scala.tools.scalap.scalax.util.StringUtil

sealed abstract class Verbosity
case object ShowAll extends Verbosity
case object HideClassPrivate extends Verbosity
case object HideInstancePrivate extends Verbosity

class ScalaSigPrinter(stream: PrintStream, verbosity: Verbosity) {
  import stream._

  def this(stream: PrintStream, printPrivates: Boolean) = this(stream: PrintStream, if (printPrivates) ShowAll else HideClassPrivate)

  private val currentTypeParameters: mutable.HashMap[TypeSymbol, String] = new mutable.HashMap[TypeSymbol, String]()

  private def addTypeParameter(t: TypeSymbol) {
    def checkName(name: String): Boolean = {
      currentTypeParameters.forall {
        case (symbol: TypeSymbol, symbolName: String) => name != symbolName
      }
    }
    if (checkName(t.name)) {
      currentTypeParameters += ((t, t.name))
    } else {
      @tailrec
      def writeWithIndex(index: Int) {
        val nameWithIndex: String = s"${t.name}_$$_$index"
        if (checkName(nameWithIndex)) {
          currentTypeParameters += ((t, nameWithIndex))
        } else writeWithIndex(index + 1)
      }
      writeWithIndex(1)
    }
  }

  private def removeTypeParameter(t: TypeSymbol) {
    currentTypeParameters.remove(t)
  }

  val CONSTRUCTOR_NAME = "<init>"

  val INIT_NAME = "$init$"

  case class TypeFlags(printRep: Boolean)

  def printSymbol(symbol: Symbol) {printSymbol(0, symbol)}

  def printSymbolAttributes(s: Symbol, onNewLine: Boolean, indent: => Unit) = s match {
    case t: SymbolInfoSymbol => {
      for (a <- t.attributes) {
        indent; print(toString(a))
        if (onNewLine) print("\n") else print(" ")
      }
    }
    case _ =>
  }

  def printSymbol(level: Int, symbol: Symbol) {
    if (symbol.isSynthetic) return
    val shouldPrint = {
      val accessibilityOk = verbosity match {
        case ShowAll => true
        case HideClassPrivate => !symbol.isPrivate || symbol.isInstanceOf[AliasSymbol] || level == 0
        case HideInstancePrivate => !symbol.isLocal || symbol.isInstanceOf[AliasSymbol]
      }
      val paramAccessor = symbol match {
        case m: MethodSymbol if m.isParamAccessor => true
        case _ => false
      }
      accessibilityOk && !symbol.isCaseAccessor && !paramAccessor
    }
    if (shouldPrint) {
      def indent() {for (i <- 1 to level) print("  ")}

      printSymbolAttributes(symbol, true, indent)
      symbol match {
        case o: ObjectSymbol =>
          indent
          if (o.name == "package" || o.name == "`package`") {
            // print package object
            printPackageObject(level, o)
          } else {
            printObject(level, o)
          }
        case c: ClassSymbol if !refinementClass(c) && !c.isModule =>
          indent
          printClass(level, c)
        case m: MethodSymbol =>
          printMethod(level, m, indent)
        case a: AliasSymbol =>
          indent
          printAlias(level, a)
        case t: TypeSymbol if !t.isParam && !t.name.matches("_\\$\\d+") &&
          !t.name.matches("\\?(\\d)+") =>
          // todo: type 0? found in Suite class from scalatest package. So this is quickfix,
          // todo: we need to find why such strange type is here
          indent
          printTypeSymbol(level, t)
        case s =>
      }
    }
  }

  def isCaseClassObject(o: ObjectSymbol): Boolean = {
    val TypeRefType(prefix, classSymbol: ClassSymbol, typeArgs) = o.infoType
    o.isFinal && (classSymbol.children.find(x => x.isCase && x.isInstanceOf[MethodSymbol]) match {
      case Some(_) => true
      case None => false
    })
  }

  private def underCaseClass(m: MethodSymbol) = m.parent match {
    case Some(c: ClassSymbol) => c.isCase
    case _ => false
  }

  private def underObject(m: MethodSymbol) = m.parent match {
    case Some(c: ClassSymbol) => c.isModule
    case _ => false
  }

  private def underTrait(m: MethodSymbol) = m.parent match {
    case Some(c: ClassSymbol) => c.isTrait
    case _ => false
  }


  private def printChildren(level: Int, symbol: Symbol, filterFirstCons: Boolean = false) {
    var firstConsFiltered = !filterFirstCons
    for {
      child <- symbol.children
      if !(child.isParam && child.isType)
    } {
      if (!firstConsFiltered)
        child match {
          case m: MethodSymbol if m.name == CONSTRUCTOR_NAME => firstConsFiltered = true
          case _ => printSymbol(level + 1, child)
        }
      else printSymbol(level + 1, child)
    }
  }

  def printWithIndent(level: Int, s: String) {
    def indent() {for (i <- 1 to level) print("  ")}
    indent()
    print(s)
  }

  def printModifiers(symbol: Symbol) {
    lazy val privateWithin: Option[String] = {
      symbol match {
        case sym: SymbolInfoSymbol => sym.symbolInfo.privateWithin match {
          case Some(t: Symbol) => Some("[" + processName(t.name) + "]")
          case _ => None
        }
        case _ => None
      }
    }

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
    if (symbol.isOverride) print("override ")
    if (symbol.isAbstract) symbol match {
      case c@(_: ClassSymbol | _: ObjectSymbol) if !c.isTrait => print("abstract ")
      case _ => ()
    }
    if (symbol.isCase && !symbol.isMethod) print("case ")
  }

  private def refinementClass(c: ClassSymbol) = c.name == "<refinement>"

  def printClass(level: Int, c: ClassSymbol) {
    if (c.name == "<local child>" /*scala.tools.nsc.symtab.StdNames.LOCALCHILD.toString()*/ ) {
      print("\n")
    } else if (c.name == "<refinement>") { //todo: make it better to avoin '\n' char
      print(" {\n")
      printChildren(level, c)
      printWithIndent(level, "}")
    } else {
      printModifiers(c)
      val defaultConstructor = if (!c.isTrait) getPrinterByConstructor(c) else ""
      if (c.isTrait) print("trait ") else print("class ")
      print(processName(c.name))
      val it = c.infoType
      val (classType, typeParams) = it match {
        case PolyType(typeRef, symbols) => (PolyTypeWithCons(typeRef, symbols, defaultConstructor), symbols)
        case ClassInfoType(a, b) if !c.isTrait => (ClassInfoTypeWithCons(a, b, defaultConstructor), Seq.empty)
        case _ => (it, Seq.empty)
      }
      for (param <- typeParams) addTypeParameter(param)
      printType(classType)
      try {
        print(" {")
        //Print class selftype
        c.selfType match {
          case Some(t: Type) => print("\n"); print(" this : " + toString(t) + " =>")
          case None =>
        }
        print("\n")
        printChildren(level, c, !c.isTrait)
        printWithIndent(level, "}\n")
      }
      finally {
        for (param <- typeParams) removeTypeParameter(param)
      }
    }
  }

  def getClassString(level: Int, c: ClassSymbol): String = {
    val baos = new ByteArrayOutputStream
    val stream = new PrintStream(baos)
    val printer = new ScalaSigPrinter(stream, verbosity)
    printer.printClass(level, c)
    baos.toString
  }

  def getPrinterByConstructor(c: ClassSymbol): String = {
    c.children.find {
      case m: MethodSymbol if m.name == CONSTRUCTOR_NAME => true
      case _ => false
    } match {
      case Some(m: MethodSymbol) =>
        val baos = new ByteArrayOutputStream
        val stream = new PrintStream(baos)
        val printer = new ScalaSigPrinter(stream, verbosity)
        printer.printPrimaryConstructor(m, c)
        val res = baos.toString
        if (res.length() > 0 && res.charAt(0) != '(') " " + res
        else res
      case _ => ""
    }
  }

  def printPrimaryConstructor(m: MethodSymbol, c: ClassSymbol) {
    printModifiers(m)
    printMethodType(m.infoType, false, methodSymbolAsClassParam(_, c))(())
  }

  def printPackageObject(level: Int, o: ObjectSymbol) {
    printModifiers(o)
    print("package ")
    print("object ")
    val poName = o.symbolInfo.owner.name
    print(processName(poName))
    val TypeRefType(prefix, classSymbol: ClassSymbol, typeArgs) = o.infoType
    printType(classSymbol)
    print(" {\n")
    printChildren(level, classSymbol)
    printWithIndent(level, "}\n")

  }

  def printObject(level: Int, o: ObjectSymbol) {
    printModifiers(o)
    print("object ")
    print(processName(o.name))
    val TypeRefType(prefix, classSymbol: ClassSymbol, typeArgs) = o.infoType
    printType(classSymbol)
    print(" {\n")
    printChildren(level, classSymbol)
    printWithIndent(level, "}\n")
  }

  def genParamNames(t: {def paramTypes: Seq[Type]}): List[String] = t.paramTypes.toList.map(x => {
    var str = toString(x)
    val j = str.indexOf("[")
    if (j > 0) str = str.substring(0, j)
    str = StringUtil.trimStart(str, "=> ")
    val i = str.lastIndexOf(".")
    val res = if (i > 0) str.substring(i + 1) else str
    if (res.length > 1) StringUtil.decapitalize(res.substring(0, 1)) else res.toLowerCase
  })

  private def methodSymbolAsMethodParam(ms: MethodSymbol): String = {
    val nameAndType = processName(ms.name) + " : " + toString(ms.infoType)(TypeFlags(true))
    val default = if (ms.hasDefault) " = { /* compiled code */ }" else ""
    nameAndType + default
  }

  private def methodSymbolAsClassParam(msymb: MethodSymbol, c: ClassSymbol): String = {
    val baos = new ByteArrayOutputStream
    val stream = new PrintStream(baos)
    val printer = new ScalaSigPrinter(stream, verbosity)
    var break = false
    for (child <- c.children if !break) {
      child match {
        case ms: MethodSymbol if ms.isParamAccessor && msymb.name == ms.name =>
          if (!ms.isPrivate || !ms.isLocal) {
            printer.printSymbolAttributes(ms, false, ())
            printer.printModifiers(ms)
            if (ms.isParamAccessor && ms.isMutable) stream.print("var ")
            else if (ms.isParamAccessor) stream.print("val ")
          }
          break = true
        case _ =>
      }
    }

    val nameAndType = processName(msymb.name) + " : " + toString(msymb.infoType)(TypeFlags(true))
    val default = if (msymb.hasDefault) " = { /* compiled code */ }" else ""
    stream.print(nameAndType + default)
    baos.toString
  }

  def printMethodType(t: Type, printResult: Boolean,
                      pe: MethodSymbol => String = methodSymbolAsMethodParam)(cont: => Unit) {

    def _pmt(mt: Type {def resultType: Type; def paramSymbols: Seq[Symbol]}) {

      val paramEntries = mt.paramSymbols.map({
        case ms: MethodSymbol => pe(ms)
        case _ => "^___^"
      })

      // Print parameter clauses
      print(paramEntries.mkString(
        "(" + (mt match {
          case _: ImplicitMethodType => "implicit "
          //for Scala 2.9
          case mt: MethodType if mt.paramSymbols.length > 0 && mt.paramSymbols(0).isImplicit => "implicit "
          case _ => ""
        }), ", ", ")"))

      // Print result type
      mt.resultType match {
        case mt: MethodType => printMethodType(mt, printResult, pe)({})
        case imt: ImplicitMethodType => printMethodType(imt, printResult, pe)({})
        case x => if (printResult) {
          print(" : ")
          printType(x)
        }
      }
    }

    t match {
      case mt@MethodType(resType, paramSymbols) => _pmt(mt)
      case mt@ImplicitMethodType(resType, paramSymbols) => _pmt(mt)
      case pt@PolyType(mt, typeParams) => {
        for (param <- typeParams) addTypeParameter(param)
        print(typeParamString(typeParams))
        try {
          printMethodType(mt, printResult)({})
        }
        finally {
          for (param <- typeParams) removeTypeParameter(param)
        }
      }
      //todo consider another method types
      case x => print(" : "); printType(x)
    }

    // Print rest of the symbol output
    cont
  }

  def printMethod(level: Int, m: MethodSymbol, indent: () => Unit) {
    def cont {print(" = { /* compiled code */ }")}

    val n = m.name
    if (underObject(m) && n == CONSTRUCTOR_NAME) return
    if (underTrait(m) && n == INIT_NAME) return
    if (n.matches(".+\\$default\\$\\d+")) return // skip default function parameters
    if (n.startsWith("super$")) return // do not print auxiliary qualified super accessors
    if (m.isAccessor && n.endsWith("_$eq")) return
    if (m.isParamAccessor) return //do not print class parameters
    indent()
    printModifiers(m)
    if (m.isAccessor) {
      val indexOfSetter = m.parent.get.children.indexWhere(x =>
        x.isInstanceOf[MethodSymbol] &&
                x.asInstanceOf[MethodSymbol].name == n + "_$eq")
      print(if (indexOfSetter > 0) "var " else "val ")
    } else {
      print("def ")
    }
    n match {
      case CONSTRUCTOR_NAME =>
        print("this")
        printMethodType(m.infoType, false)(cont)
      case name =>
        val nn = processName(name)
        print(nn)
        val printBody = !m.isDeferred && (m.parent match {
          case Some(c: ClassSymbol) if refinementClass(c) => false
          case _ => true
        })
        printMethodType(m.infoType, true)(
          {if (printBody) print(" = { /* compiled code */ }" /* Print body only for non-abstract methods */ )}
          )
    }
    print("\n")
  }

  def printAlias(level: Int, a: AliasSymbol) {
    printModifiers(a)
    print("type ")
    print(processName(a.name))
    val tp = a.infoType match {
      case PolyType(typeRef, symbols) => printType(PolyTypeWithCons(typeRef, symbols, " = "))
      case tp => printType(tp, " = ")
    }
    print("\n")
    printChildren(level, a)
  }

  def printTypeSymbol(level: Int, t: TypeSymbol) {
    print("type ")
    print(processName(t.name))
    t.infoType match {
      case PolyType(typeRef, symbols) => printType(PolyTypeWithCons(typeRef, symbols, ""))
      case _ => printType(t.infoType)
    }
    print("\n")
  }

  def toString(attrib: AttributeInfo): String = {
    val buffer = new StringBuffer
    buffer.append(toString(attrib.typeRef, "@"))
    if (attrib.value.isDefined) {
      buffer.append("(")
      val value = attrib.value.get
      val stringVal = value.isInstanceOf[String]
      if (stringVal) buffer.append("\"")
      val stringValue = valueToString(value)
      val isMultiline = stringVal && (stringValue.contains("\n")
              || stringValue.contains("\r"))
      if (isMultiline) buffer.append("\"\"")
      buffer.append(valueToString(value))
      if (isMultiline) buffer.append("\"\"")
      if (stringVal) buffer.append("\"")
      buffer.append(")")
    }
    if (!attrib.values.isEmpty) {
      buffer.append(" {")
      for (name ~ value <- attrib.values) {
        buffer.append(" val ")
        buffer.append(processName(name))
        buffer.append(" = ")
        buffer.append(valueToString(value))
      }
      buffer.append(" }")
    }
    buffer.toString
  }

  def valueToString(value: Any): String = value match {
    case t: Type => "classOf[%s]" format toString(t)
    // TODO string, char, float, etc.
    case _ => value.toString
  }

  implicit object _tf extends TypeFlags(false)

  def printType(sym: SymbolInfoSymbol)(implicit flags: TypeFlags): Unit = printType(sym.infoType)(flags)

  def printType(t: Type)(implicit flags: TypeFlags): Unit = print(toString(t)(flags))

  def printType(t: Type, sep: String)(implicit flags: TypeFlags): Unit = print(toString(t, sep)(flags))

  def toString(t: Type)(implicit flags: TypeFlags): String = toString(t, "")(flags)

  private val SingletonTypePattern = """(.*?)\.type""".r

  def toString(t: Type, sep: String)(implicit flags: TypeFlags): String = {

    // print type itself
    t match {
      case ThisType(classSymbol: ClassSymbol) if refinementClass(classSymbol) => sep + "this.type"
      case ThisType(symbol) => sep + processName(symbol.name) + ".this.type"
      case SingleType(ThisType(thisSymbol: ClassSymbol), symbol) =>
        val thisSymbolName: String =
          thisSymbol.name match {
            case "package" => thisSymbol.symbolInfo.owner match {
              case ex: ExternalSymbol => processName(ex.name)
              case _ => "this"
            }
            case name => processName(name) + ".this"
          }
        sep + thisSymbolName + "." + processName(symbol.name) + ".type"
      case SingleType(ThisType(exSymbol: ExternalSymbol), symbol) if exSymbol.name == "<root>" =>
        sep + processName(symbol.name) + ".type"
      case SingleType(ThisType(exSymbol: ExternalSymbol), symbol) =>
        sep + StringUtil.cutSubstring(StringUtil.trimStart(processName(exSymbol.path), "<empty>."))(".`package`") + "." +
          processName(symbol.name) + ".type"
      case SingleType(NoPrefixType, symbol) =>
        sep + processName(symbol.name) + ".type"
      case SingleType(typeRef, symbol) => {
        var typeRefString = toString(typeRef)
        if (typeRefString.endsWith(".type")) typeRefString = typeRefString.dropRight(5)
        typeRefString = StringUtil.cutSubstring(typeRefString)(".`package`")
        sep + typeRefString + "." + processName(symbol.name) + ".type"
      }
      case ConstantType(constant) => sep + (constant match {
        case null => "scala.Null"
        case _: Unit => "scala.Unit"
        case _: Boolean => "scala.Boolean"
        case _: Byte => "scala.Byte"
        case _: Char => "scala.Char"
        case _: Short => "scala.Short"
        case _: Int => "scala.Int"
        case _: Long => "scala.Long"
        case _: Float => "scala.Float"
        case _: Double => "scala.Double"
        case _: String => "java.lang.String"
        case c: Class[_] => "java.lang.Class[" + c.getComponentType.getCanonicalName.replace("$", ".") + "]"
        case ExternalSymbol(_, Some(parent), _) => parent.path //enum value
      })
      case TypeRefType(NoPrefixType, symbol: TypeSymbol, typeArgs) if currentTypeParameters.isDefinedAt(symbol) =>
        sep + processName(currentTypeParameters.get(symbol).getOrElse(symbol.name)) + typeArgString(typeArgs)
      case TypeRefType(prefix, symbol, typeArgs) => sep + (symbol.path match {
        case "scala.<repeated>" => flags match {
          case TypeFlags(true) => toString(typeArgs.head) + "*"
          case _ => "scala.Seq" + typeArgString(typeArgs)
        }
        case "scala.<byname>" => "=> " + toString(typeArgs.head)
        case _ => {
          def checkContainsSelf(self: Option[Type], parent: Symbol): Boolean = {
            self match {
              case Some(tp) =>
                tp match {
                  case ThisType(symbol) => symbol == parent
                  case SingleType(_, symbol) => symbol == parent
                  case c: ConstantType => false
                  case TypeRefType(_, symbol, _) => symbol == parent
                  case t: TypeBoundsType => false
                  case RefinedType(symbol, refs) => symbol == parent || !refs.forall(tp => !checkContainsSelf(Some(tp), parent))
                  case ClassInfoType(symbol, refs) => symbol == parent || !refs.forall(tp => !checkContainsSelf(Some(tp), parent))
                  case ClassInfoTypeWithCons(symbol, refs, _) => symbol == parent || !refs.forall(tp => !checkContainsSelf(Some(tp), parent))
                  case ImplicitMethodType(resultType, _) => false
                  case MethodType(resultType, _) => false
                  case NullaryMethodType(resultType) => false
                  case PolyType(typeRef, symbols) =>
                    checkContainsSelf(Some(typeRef), parent) || symbols.find(_ == parent).nonEmpty
                  case PolyTypeWithCons(typeRef, symbols, _) =>
                    checkContainsSelf(Some(typeRef), parent) || symbols.find(_ == parent).nonEmpty
                  case AnnotatedType(typeRef, _) => checkContainsSelf(Some(typeRef), parent)
                  case AnnotatedWithSelfType(typeRef, symbol, _) =>
                    checkContainsSelf(Some(typeRef), parent) || symbol == parent
                  case ExistentialType(typeRef, symbols) =>
                    checkContainsSelf(Some(typeRef), parent) || symbols.find(_ == parent).nonEmpty
                  case _ => false
                }
              case None => false
            }
          }
          val prefixStr = (prefix, symbol, toString(prefix)) match {
            case (NoPrefixType, _, _) => ""
            case (ThisType(objectSymbol), _, _) if objectSymbol.isModule && !objectSymbol.isStable =>
              processName(objectSymbol.name) + "."
            case (ThisType(packSymbol), _, _) if !packSymbol.isType =>
              processName(packSymbol.path) + "."
            case (ThisType(classSymbol: ClassSymbol), _, _) if refinementClass(classSymbol) => ""
            case (ThisType(typeSymbol: ClassSymbol), ExternalSymbol(_, Some(parent), _), _)
              if typeSymbol.path != parent.path && checkContainsSelf(typeSymbol.selfType, parent) =>
              processName(typeSymbol.name) + ".this."
            case (ThisType(typeSymbol), ExternalSymbol(_, Some(parent), _), _) if typeSymbol.path != parent.path =>
              processName(typeSymbol.name) + ".super[" + processName(parent.name) + "/*"+ parent.path +"*/]."
            case (_, _, SingletonTypePattern(a)) => a + "."
            case (_, _, a) => a + "#"
          }
          //remove package object reference
          val path = StringUtil.cutSubstring(prefixStr)(".`package`")
          val name = processName(symbol.name)
          val res = path + name
          val typeBounds = if (name == "_") {
            symbol match {
              case ts: TypeSymbol =>
                ts.infoType match {
                  case t: TypeBoundsType => toString(t)
                  case _ => ""
                }
              case _ => ""
            }
          } else ""
          val ress = StringUtil.trimStart(res, "<empty>.") + typeArgString(typeArgs) + typeBounds
          ress
        }
      })
      case TypeBoundsType(lower, upper) => {
        val lb = toString(lower)
        val ub = toString(upper)
        val lbs = if (!lb.equals("scala.Nothing")) " >: " + lb else ""
        val ubs = if (!ub.equals("scala.Any")) " <: " + ub else ""
        lbs + ubs
      }
      case RefinedType(classSym: ClassSymbol, typeRefs) =>
        val classStr = getClassString(0, classSym)
        sep + typeRefs.map(toString).mkString("", " with ", "") + (if (classStr == " {\n}") "" else classStr)
      case RefinedType(classSym, typeRefs) => sep + typeRefs.map(toString).mkString("", " with ", "")
      case ClassInfoType(symbol, typeRefs) => sep + typeRefs.map(toString).mkString(" extends ", " with ", "")
      case ClassInfoTypeWithCons(symbol, typeRefs, cons) => sep + typeRefs.map(toString).
              mkString(cons + " extends ", " with ", "")

      case ImplicitMethodType(resultType, _) => toString(resultType, sep)
      case MethodType(resultType, _) => toString(resultType, sep)
      case NullaryMethodType(resultType) => toString(resultType, sep)

      case PolyType(typeRef, symbols) =>
        "({ type λ" + typeParamString(symbols) + " = " + toString(typeRef, sep) + " })#λ"
      case PolyTypeWithCons(typeRef, symbols, cons) =>
        typeParamString(symbols) + cons + toString(typeRef, sep)
      case AnnotatedType(typeRef, attribTreeRefs) => {
        toString(typeRef, sep)
      }
      case AnnotatedWithSelfType(typeRef, symbol, attribTreeRefs) => toString(typeRef, sep)
      //case DeBruijnIndexType(typeLevel, typeIndex) =>
      case ExistentialType(typeRef, symbols) => {
        val refs = symbols.map(toString _).filter(!_.startsWith("_")).map("type " + _)
        toString(typeRef, sep) + (if (refs.size > 0) refs.mkString(" forSome {", "; ", "}") else "")
      }
      case _ => sep + t.toString
    }
  }

  def getVariance(t: TypeSymbol) = if (t.isCovariant) "+" else if (t.isContravariant) "-" else ""

  def toString(symbol: Symbol): String = symbol match {
    case symbol: TypeSymbol => {
      val attrs = (for (a <- symbol.attributes) yield toString(a)).mkString(" ")
      val atrs = if (attrs.length > 0) attrs.trim + " " else ""
      val symbolType = symbol.infoType match {
        case PolyType(typeRef, symbols) => PolyTypeWithCons(typeRef, symbols, "")
        case tp => tp
      }
      val name: String = currentTypeParameters.get(symbol).getOrElse(symbol.name)
      atrs + getVariance(symbol) + processName(name) + toString(symbolType)
    }
    case s => symbol.toString
  }

  def typeArgString(typeArgs: Seq[Type]): String =
    if (typeArgs.isEmpty) ""
    else typeArgs.map(toString).map(StringUtil.trimStart(_, "=> ")).mkString("[", ", ", "]")

  def typeParamString(params: Seq[TypeSymbol]): String =
    if (params.isEmpty) ""
    else params.map(toString).mkString("[", ", ", "]")

  val _syms = Map("\\$bar" -> "|", "\\$tilde" -> "~",
    "\\$bang" -> "!", "\\$up" -> "^", "\\$plus" -> "+",
    "\\$minus" -> "-", "\\$eq" -> "=", "\\$less" -> "<",
    "\\$times" -> "*", "\\$div" -> "/", "\\$bslash" -> "\\\\",
    "\\$greater" -> ">", "\\$qmark" -> "?", "\\$percent" -> "%",
    "\\$amp" -> "&", "\\$colon" -> ":", "\\$u2192" -> "→",
    "\\$hash" -> "#")
  val pattern = Pattern.compile(_syms.keys.foldLeft("")((x, y) => if (x == "") y else x + "|" + y))
  val placeholderPattern = "_\\$(\\d)+"

  private val keywordList =
    Set("true", "false", "null", "abstract", "case", "catch", "class", "def",
        "do", "else", "extends", "final", "finally", "for", "forSome", "if",
        "implicit", "import", "lazy", "match", "new", "object", "override",
        "package", "private", "protected", "return", "sealed", "super",
        "this", "throw", "trait", "try", "type", "val", "var", "while", "with",
        "yield")

  private def stripPrivatePrefix(name: String) = {
    val i = name.lastIndexOf("$$")
    if (i > 0) name.substring(i + 2) else name
  }
  
  def processName(name: String): String = {
    def processNameWithoutDot(name: String) = {
      def isIdentifier(id: String): Boolean = {
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
          case c => isSpecial(c)
        }


        if (id.isEmpty) return false
        if (isIdentifierStart(id(0))) {
          if (id.indexWhere(c => !isIdentifierPart(c) && !isOperatorPart(c) && c != '_') >= 0) return false
          val index = id.indexWhere(isOperatorPart(_))
          if (index < 0) return true
          if (id(index - 1) != '_') return false
          id.drop(index).forall(isOperatorPart(_))
        } else if (isOperatorPart(id(0))) {
          id != "|" && id.forall(isOperatorPart(_))
        } else false
      }
      val result = NameTransformer.decode(name)
      if (!isIdentifier(result) || keywordList.contains(result) || result == "=") "`" + result + "`" else result
    }
    val stripped = stripPrivatePrefix(name)
    val m = pattern.matcher(stripped)
    var temp = stripped
    while (m.find) {
      val key = m.group
      val re = "\\" + key
      temp = temp.replaceAll(re, _syms(re))
    }
    var result = temp.replaceAll(placeholderPattern, "_")

    //to avoid names like this one: ?0 (from existential type parameters)
    if (result.length() > 1 && result(0) == '?' && result(1).isDigit) result = "x" + result.substring(1)
    result.split('.').map(s => processNameWithoutDot(s)).mkString(".")
  }
}

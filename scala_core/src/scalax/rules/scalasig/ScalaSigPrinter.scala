package scalax.rules.scalasig

object ScalaSigPrinter {

  def printSymbol(symbol: Symbol) {printSymbol(0, symbol)}

  def printSymbol(level: Int, symbol: Symbol) {
    def indent() {for (i <- 1 to level) print("  ")}


    symbol match {
      case o: ObjectSymbol => indent(); printObject(level, o)
      case c: ClassSymbol => indent(); printClass(level, c)
      case m: MethodSymbol => indent(); printMethod(level, m)
      case a: AliasSymbol => indent(); printAlias(level, a)
      case t: TypeSymbol => ()
      case s => {}
    }
  }

  def printChildren(level: Int, symbol: Symbol) {
    for (child <- symbol.children) printSymbol(level + 1, child)
  }

  def printWithIndent(level: Int, s: String) {
    def indent() {for (i <- 1 to level) print("  ")}
    indent;
    print(s)
  }

  def printModifiers(symbol: Symbol) {
    if (symbol.isSealed) print("sealed ")
    if (symbol.isPrivate) print("private ")
    else if (symbol.isProtected) print("protected ")
    if (symbol.isAbstract) symbol match {
      case c@(_: ClassSymbol | _: ObjectSymbol) if !c.isTrait => print("abstract ")
      case _ => ()
    }
    if (symbol.isCase && !symbol.isMethod) print("case ")
  }

  def printClass(level: Int, c: ClassSymbol) {
    printAttributes(c)
    printModifiers(c)
    if (c.isTrait) print("trait ") else print("class ")
    print(processName(c.name))
    printType(c)
    print("{\n")
    printChildren(level, c)
    printWithIndent(level, "}\n")
  }

  def printObject(level: Int, o: ObjectSymbol) {
    printAttributes(o)
    printModifiers(o)
    print("object ")
    print(processName(o.name))
    val TypeRefType(prefix, symbol: ClassSymbol, typeArgs) = o.infoType
    printType(symbol)
    print("{\n")
    printChildren(level, symbol)
    printWithIndent(level, "}\n")
  }

  def printMethod(level: Int, m: MethodSymbol) {
    printAttributes(m)
    printModifiers(m)
    print("def ")
    m.name match {
      case "<init>" => {
        print("this")
        print(m.infoType match {
          case MethodType(_, paramTypes) => {
            paramTypes.map(toString).map(x => genParamName(x) + ": " + x).mkString("(", ", ", ")")
          }
        })
        print(" = { /* compiled code */ }")
      }
      case name => {
        val nn = processName(name)
        print(nn)
        printType(m.infoType, " : ")
        if (!m.isAbstract) { // Print body for non-abstract metods
          print(" = { /* compiled code */ }")
        }
      }
    }
    println()
    printChildren(level, m)
  }

  def printAlias(level: Int, a: AliasSymbol) {
    printAttributes(a)
    print("type ")
    print(processName(a.name))
    printType(a.infoType, " = ")
    println()

    printChildren(level, a)
  }

  def printAttributes(sym: SymbolInfoSymbol) {
    for (attrib <- sym.attributes) printAttribute(attrib)
  }

  def printAttribute(attrib: AttributeInfo) {
    printType(attrib.typeRef, "@")
    if (attrib.value.isDefined) {
      print("(")
      printValue(attrib.value.get)
      print(")")
    }
    if (!attrib.values.isEmpty) {
      print(" {")
      for (name ~ value <- attrib.values) {
        print(" val ")
        print(processName(name))
        print(" = ")
        printValue(value)
      }
      printValue(attrib.value)
      print(" }")
    }
    print(" ")
  }

  def printValue(value: Any): Unit = value match {
    case t: Type => printType(t)
    // TODO string, char, float, etc.
    case _ => print(value)
  }

  def printType(sym: SymbolInfoSymbol): Unit = printType(sym.infoType)

  def printType(t: Type): Unit = print(toString(t))

  def printType(t: Type, sep: String): Unit = print(toString(t, sep))

  def toString(t: Type): String = toString(t, "")

  def genParamName(ts: String) = "o" //todo improve name generation by type

  def toString(t: Type, sep: String): String = t match {
  //case ThisType(symbol) =>
    case SingleType(typeRef, symbol) => sep + toString(symbol) + " with Singleton"
    //case ConstantType(typeRef, constant) =>
    case TypeRefType(prefix, symbol, typeArgs) => symbol.path match {
      case "scala.<repeated>" if typeArgs.length == 1 => sep + toString(typeArgs.first, "") + "*"
      case "scala.<byname>" if typeArgs.length == 1 => "=> " + sep + toString(typeArgs.first, "")
      case _ => sep + symbol.path + typeArgString(typeArgs)
    }
    case TypeBoundsType(lower, upper) => " >: " + toString(lower) + " <: " + toString(upper)
    //case RefinedType(classSymRef, typeRefs) => 
    case ClassInfoType(symbol, typeRefs) => typeRefs.map(toString).mkString(" extends ", " with ", "")

    case MethodType(resultType, paramTypes) => paramTypes.map(toString).map(x => genParamName(x) + ": " + x).mkString("(", ", ", ") : ") + toString(resultType)

    case PolyType(typeRef, symbols) => typeParamString(symbols) + toString(typeRef, sep)
    //case ImplicitMethodType(resultType, paramTypes) => 
    //case AnnotatedType(typeRef, attribTreeRefs) => 
    //case AnnotatedWithSelfType(typeRef, symbol, attribTreeRefs) => 
    //case DeBruijnIndexType(typeLevel, typeIndex) => 
    //case ExistentialType(typeRef, symbols) => 
    case _ => sep + t.toString
  }

  def toString(symbol: Symbol): String = symbol match {
    case symbol: TypeSymbol => processName(symbol.name) + toString(symbol.infoType)
    case s => symbol.toString
  }

  def typeArgString(typeArgs: Seq[Type]): String =
    if (typeArgs.isEmpty) ""
    else typeArgs.map(toString).mkString("[", ", ", "]")

  def typeParamString(params: Seq[Symbol]): String =
    if (params.isEmpty) ""
    else params.map(toString).mkString("[", ", ", "]")

  def processName(name: String) = name.replaceAll("\\$bar", "|").replaceAll("\\$tilde", "~").
          replaceAll("\\$bang", "!").replaceAll("\\$up", "^").replaceAll("\\$plus", "+").
          replaceAll("\\$minus", "-").replaceAll("\\$eq", "=").replaceAll("\\$less", "<").
          replaceAll("\\$times", "*").replaceAll("\\$div", "/").replaceAll("\\$bslash", "\\\\").
          replaceAll("\\$greater", ">").replaceAll("\\$qmark", "?").replaceAll("\\$percent", "%").
          replaceAll("\\$amp", "&").replaceAll("\\$colon", ":").replaceAll("\\$u2192", "→")

}

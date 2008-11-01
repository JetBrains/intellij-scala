package scalax.rules.scalasig

object ScalaSigPrinter {

  type Printer = (String) => Unit

  implicit def getPrinter: Printer = print _

  def printSymbol(symbol: Symbol, printer: Printer) {printSymbol(0, symbol)(printer)}

  def printSymbol(symbol: Symbol) {printSymbol(0, symbol)}

  def printSymbol(level: Int, symbol: Symbol)(implicit printer: Printer) {
    def indent() {for (i <- 1 to level) print("  ")}


    symbol match {
      case o: ObjectSymbol => indent(); printObject(level, o)(printer)
      case c: ClassSymbol => indent(); printClass(level, c)(printer)
      case m: MethodSymbol => indent(); printMethod(level, m)(printer)
      case a: AliasSymbol => indent(); printAlias(level, a) (printer)
      case t: TypeSymbol => ()
      case s => {}
    }
  }

  def printChildren(level: Int, symbol: Symbol) {
    for (child <- symbol.children) printSymbol(level + 1, child)
  }

  def printWithIndent(level: Int, s: String)(implicit printer: Printer) {
    def indent() {for (i <- 1 to level) printer("  ")}
    indent;
    printer(s)
  }

  def printModifiers(symbol: Symbol)(implicit printer: Printer) {
    if (symbol.isSealed) printer("sealed ")
    if (symbol.isPrivate) printer("private ")
    else if (symbol.isProtected) printer("protected ")
    if (symbol.isAbstract) printer("abstract ")
    if (symbol.isCase && !symbol.isMethod) printer("case ")
  }

  def printClass(level: Int, c: ClassSymbol)(implicit printer: Printer) {
    printAttributes(c)
    printModifiers(c)(printer)
    if (c.isTrait) printer("trait ") else printer("class ")
    printer(c.name)
    printType(c)
    printer("{\n")
    printChildren(level, c)
    printWithIndent(level, "}\n")(printer)
  }

  def printObject(level: Int, o: ObjectSymbol)(implicit printer: Printer) {
    printAttributes(o)
    printModifiers(o)(printer)
    printer("object ")
    printer(o.name)
    val TypeRefType(prefix, symbol: ClassSymbol, typeArgs) = o.infoType
    printType(symbol)
    printer("\n")
    printChildren(level, symbol)
    printWithIndent(level, "}\n")(printer)
  }

  def printMethod(level: Int, m: MethodSymbol)(implicit printer: Printer) {
    printAttributes(m)
    printModifiers(m)(printer)
    printer("def ")
    printer(m.name match {case "<init>" => "this" case name => name})
    printType(m.infoType, " : ")(printer)
    println()

    printChildren(level, m)
  }

  def printAlias(level: Int, a: AliasSymbol)(implicit printer: Printer) {
    printAttributes(a)
    printer("type ")
    printer(a.name)
    printType(a.infoType, " = ")(printer)
    println()

    printChildren(level, a)
  }

  def printAttributes(sym: SymbolInfoSymbol) {
    for (attrib <- sym.attributes) printAttribute(attrib)
  }

  def printAttribute(attrib: AttributeInfo)(implicit printer: Printer) {
    printType(attrib.typeRef, "@")(printer)
    if (attrib.value.isDefined) {
      printer("(")
      printValue(attrib.value.get)(printer)
      printer(")")
    }
    if (!attrib.values.isEmpty) {
      printer(" {")
      for (name ~ value <- attrib.values) {
        printer(" val ")
        printer(name)
        printer(" = ")
        printValue(value)(printer)
      }
      printValue(attrib.value)(printer)
      printer(" }")
    }
    printer(" ")
  }

  def printValue(value: Any)(implicit printer: Printer): Unit = value match {
    case t: Type => printType(t)(printer)
    // TODO string, char, float, etc.
    case _ => printer(value.toString)
  }

  def printType(sym: SymbolInfoSymbol): Unit = printType(sym.infoType)

  def printType(t: Type)(implicit printer: Printer): Unit = printer(toString(t))

  def printType(t: Type, sep: String)(implicit printer: Printer): Unit = printer(toString(t, sep))

  def toString(t: Type): String = toString(t, "")

  def genParamName(ts: String) = "0" //todo improve name generation by type

  def toString(t: Type, sep: String): String = t match {
  //case ThisType(symbol) =>
  //case SingleType(typeRef, symbol) =>
  //case ConstantType(typeRef, constant) =>
    case TypeRefType(prefix, symbol, typeArgs) => sep + symbol.path + typeArgString(typeArgs)
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
    case symbol: TypeSymbol => symbol.name + toString(symbol.infoType)
    case s => symbol.toString
  }

  def typeArgString(typeArgs: Seq[Type]): String =
    if (typeArgs.isEmpty) ""
    else typeArgs.map(toString).mkString("[", ", ", "]")

  def typeParamString(params: Seq[Symbol]): String =
    if (params.isEmpty) ""
    else params.map(toString).mkString("[", ", ", "]")
}

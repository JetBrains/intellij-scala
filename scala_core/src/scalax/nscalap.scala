package scalax


object nscalap {
  import java.io.File
  import scala.tools.nsc._
  import reporters._
  import symtab.classfile._
  import util._

  def error(s: String) { println("!! ERROR: " + s) }

  def nl = println()

  def info(name: String, what: AnyRef) {
    nl
    println(name + " [" + what.getClass.getName + "]: " + what)
  }

  def main(args: Array[String]) {
    // I can use something like (I am in Windows):
    //   List("-classpath", "<scala-library.jar>;<scala-compiler.jar>")
    // or set the CLASSPATH environment variable properly
    val command = new GenericRunnerCommand(List()/*List("-verbose", "-Ydebug")*/, error)
    val settings = command.settings
    val reporter = new ConsoleReporter(settings)
    val _global = new Global(settings, reporter)
    val cfparser = new ClassfileParser {
      override val global = _global
      def _clazz = clazz
    }

    //val global = cfparser.global

    val classpath = settings.classpath.value
    val bootclasspath = settings.bootclasspath.value
    val fullClasspath = classpath + File.pathSeparator + bootclasspath

    //settings.classpath.value = settings.bootclasspath.value

    println("classpath    : " + classpath)
    println("bootclasspath: " + bootclasspath)
    println("extdirs      : " + settings.extdirs.value)
    println("Xcodebase    : " + settings.Xcodebase.value)

    info("loaders", cfparser.global.loaders)
    info("rootLoader", cfparser.global.rootLoader)

    //_global.definitions.getClass().getMethods.foreach(println)
    //settings.getClass().getMethods.foreach(println)

    val run = new cfparser.global.Run
    val definitions = cfparser.global.definitions

    println("definitions: " + definitions)
    println("initialized: " + definitions.isDefinitionsInitialized)

    val RootPackage = definitions.RootPackage
    val RootClass   = definitions.RootClass

    val ListClass = definitions.ListClass
    info("ListClass", ListClass)

    val nameToSearch = if(args.length > 0) args(0) else "scala.collection.immutable.Set"
    val nameToSearchF = nameToSearch.replace('.', File.separatorChar)

    val name = cfparser.global.newTermName(nameToSearch)
    info("name", name)
    //cfparser.global.showDef(name, false)
    val namesym = definitions.getClass(name)
    info("requested", namesym)

    val classPath0 = new ClassPath(false)
    val path = new classPath0.Build(fullClasspath)
    val abstractFile = path.lookupPath(nameToSearchF, false)
    info("path", path)
    info("abstractFile", abstractFile)

    val ScalaSignatureATTR = cfparser.global.nme.ScalaSignatureATTR
    info("ScalaSignatureATTR", ScalaSignatureATTR)

    cfparser.parse(abstractFile, namesym)

    info("namesym.info", namesym.info)

    cfparser._clazz.asInstanceOf[cfparser.global.ClassSymbol].classFile = abstractFile

    info("cfparser._clazz.info", cfparser._clazz.info)
    info("cfparser._clazz.rawInfo", cfparser._clazz.rawInfo)
    info("cfparser._clazz.tpe",  cfparser._clazz.tpe)

    val decls = cfparser._clazz.tpe.decls
    val members = cfparser._clazz.tpe.members
    info("cfparser._clazz.decls",  decls)
    info("cfparser._clazz.tpe.members",  members)

    //info("cfparser._clazz.tpe.deferredMembers",  cfparser._clazz.tpe.deferredMembers)
    //info("cfparser._clazz.tpe",  cfparser.global.debugString(cfparser._clazz.tpe))
    info("cfparser._clazz.typeConstructor",  cfparser._clazz.typeConstructor)
    info("cfparser._clazz.typeParams",  cfparser._clazz.typeParams)
    info("cfparser._clazz.enclClass",  cfparser._clazz.enclClass)
    info("cfparser._clazz.enclMethod",  cfparser._clazz.enclMethod)
    info("cfparser._clazz.primaryConstructor",  cfparser._clazz.primaryConstructor)
    //info("cfparser._clazz.classBound",  cfparser._clazz.classBound)
    info("cfparser._clazz.isInitialized", Boolean.box(cfparser._clazz.isInitialized))
    info("cfparser._clazz.isDeferred",  Boolean.box(cfparser._clazz.isDeferred))
    info("cfparser._clazz.isEarly",  Boolean.box(cfparser._clazz.isEarly))
    info("cfparser._clazz.isTerm",  Boolean.box(cfparser._clazz.isTerm))
    info("cfparser._clazz.exists",  Boolean.box(cfparser._clazz.exists))
    info("cfparser._clazz.isFromClassFile",  Boolean.box(cfparser._clazz.isFromClassFile))
    info("cfparser._clazz.allOverriddenSymbols",  cfparser._clazz.allOverriddenSymbols)
    info("cfparser._clazz.owner",  cfparser._clazz.owner)
    info("cfparser._clazz.name",  cfparser._clazz.name)
    info("cfparser._clazz.originalName",  cfparser._clazz.originalName)
    info("cfparser._clazz.simpleName",  cfparser._clazz.simpleName)
    info("cfparser._clazz.fullNameString",  cfparser._clazz.fullNameString)
    info("cfparser._clazz.toString",  cfparser._clazz.toString)
    //info("cfparser._clazz.defString",  cfparser._clazz.defString)
    //info("cfparser._clazz.infosString",  cfparser._clazz.infosString)
    info("cfparser._clazz.linkedSym",  cfparser._clazz.linkedSym)
    //info("cfparser._clazz.sourceFile",  cfparser._clazz.sourceFile)
    info("cfparser._clazz.validTo",  Int.box(cfparser._clazz.validTo))
    info("cfparser._clazz.attributes",  cfparser._clazz.attributes)


    //cfparser._clazz.getClass.getMethods.foreach(println)

    def infoDecl(sym: symtab.Symbols#Symbol) {
      println(sym + " [" + sym.tpe + "]")
    }

    decls.toList.foreach(infoDecl)

    ()
  }
}

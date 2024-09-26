package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.{Project, ProjectManagerListener}
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.psi._
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.MultiMap
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFun, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.impl.{ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitProcessor
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ResolveProcessor}
import org.jetbrains.plugins.scala.project.{ProjectContext, ScalaFeatures}
import org.jetbrains.plugins.scala.{NlsString, ScalaFileType, ScalaLanguage}

import javax.swing.Icon
import scala.collection.mutable

abstract sealed class SyntheticNamedElement(name: String)
                                           (implicit projectContext: ProjectContext)
  extends LightElement(projectContext, ScalaLanguage.INSTANCE) with PsiNameIdentifierOwner {

  override def getName: String = name
  override def getText = ""
  override def setName(newName: String) : PsiElement = throw new IncorrectOperationException("nonphysical element")
  override def copy = throw new IncorrectOperationException("nonphysical element")
  override def accept(v: PsiElementVisitor): Unit = {
    throw new IncorrectOperationException("should not call")
  }
  override def getContainingFile: PsiFile = SyntheticClasses.get(projectContext).file

  override def getNameIdentifier: PsiIdentifier = null
}

final class ScSyntheticTypeParameter(override val name: String, override val owner: ScFun)
  extends SyntheticNamedElement(name)(owner.projectContext) with ScTypeParam with PsiClassFake {

  override def nameId: PsiElement = null

  override def typeParameterText: String = name

  override def getPresentation: ItemPresentation = super[ScTypeParam].getPresentation

  override def getContainingFileName: String = "NoFile"

  override def toString: String = "Synthetic type parameter: " + name

  override def isCovariant = false
  override def isContravariant = false

  override def lowerBound: Right[Nothing, StdType] = Right(Nothing)

  override def upperBound: TypeResult = Right(Any)

  override def getIndex: Int = -1
  override def getOwner: PsiTypeParameterListOwner = null

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] =
    findChildrenByClass[T](clazz)

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = findChildByClass[T](clazz)

  override def isHigherKindedTypeParameter: Boolean = false

  override val typeParamId: Long = -1
}

// we could try and implement all type system related stuff
// with class types, but it is simpler to indicate types corresponding to synthetic classes explicitly
final class ScSyntheticClass(
  val className: String,
  val stdType: StdType
)(implicit projectContext: ProjectContext)
  extends SyntheticNamedElement(className)
    with PsiClassAdapter
    with PsiClassFake {

  override def getQualifiedName: String = "scala." + className

  override def getPresentation: ItemPresentation = {
    new ItemPresentation {
      val This: ScSyntheticClass = ScSyntheticClass.this
      override def getLocationString: String = "(scala)"

      override def getPresentableText: String = NlsString.force(This.className)

      override def getIcon(open: Boolean): Icon = This.getIcon(0)
    }
  }

  override def getNavigationElement: PsiElement = cachedInUserData("ScSyntheticClass.getNavigationElement", this, ProjectRootManager.getInstance(projectContext.project)) {
    val syntheticClassSourceMirror = for {
      scalaPackagePsiDirectory <- findScalaPackageSourcesPsiDirectory(projectContext.project)
      //class Any -> Any.scala
      psiFile <- Option(scalaPackagePsiDirectory.findFile(s"$className.scala")).map(_.asInstanceOf[ScalaFile])
      classDef <- psiFile.typeDefinitions.headOption //expecting single class definition in the file
    } yield classDef
    syntheticClassSourceMirror.getOrElse(super.getNavigationElement)
  }

  //TODO: current implementation might not work in a project with multiple scala versions. It depends on SCL-22349.
  private def findScalaPackageSourcesPsiDirectory(project: Project): Option[PsiDirectory] = cachedInUserData("ScSyntheticClass.findScalaPackageSourcesPsiDirectory", project, ProjectRootManager.getInstance(project)) {
    //Get some representative class from Scala standard library
    val classFromStdLib = ScalaPsiManager.instance(project).getCachedClass(GlobalSearchScope.allScope(project), "scala.Array")
    classFromStdLib.map { clazz =>
      //.../scala-library-2.13.11-sources.jar!/scala/Array.scala
      val navigationFile = clazz.getContainingFile.getNavigationElement.asInstanceOf[ScalaFile]
      //.../scala-library-2.13.11-sources.jar!/scala
      navigationFile.getParent;
    }
  }

  override def getNameIdentifier: PsiIdentifier = null

  override def toString = "Synthetic class"

  val syntheticMethods = new MultiMap[String, ScSyntheticFunction]()

  def addMethod(method: ScSyntheticFunction): Unit = {
    syntheticMethods.putValue(method.name, method)
    method.setContainingSyntheticClass(this)
  }

  override def processDeclarations(
    processor: com.intellij.psi.scope.PsiScopeProcessor,
    state: ResolveState,
    lastParent: PsiElement,
    place: PsiElement
  ): Boolean = {
    processor match {
      case p: ResolveProcessor =>
        val name = ScalaNamesUtil.clean(state.renamed.getOrElse(p.name))
        syntheticMethods.get(name).forEach { method =>
          if (!processor.execute(method, state)) return false
        }
      case _: ImplicitProcessor => //do nothing, there is no implicit synthetic methods
      case _: BaseProcessor =>
        //method toString and hashCode exists in java.lang.Object
        syntheticMethods.values().forEach { method =>
          if (!processor.execute(method, state)) return false
        }
      case _ => //do not execute synthetic methods to not Scala processors.
    }

    true
  }

  override def getSuperTypes: Array[PsiClassType] = {
    stdType.tSuper match {
      case None => PsiClassType.EMPTY_ARRAY
      case Some(ts) =>
        val syntheticClass = ts.syntheticClass.getOrElse(return PsiClassType.EMPTY_ARRAY)
        val factory = JavaPsiFacade.getInstance(projectContext).getElementFactory
        Array[PsiClassType](factory.createType(syntheticClass, PsiSubstitutor.EMPTY))
    }
  }
}

sealed class ScSyntheticFunction(
  val name: String,
  override val retType: ScType,
  override val paramClauses: Seq[Seq[Parameter]],
  typeParameterNames: Seq[String]
)(implicit projectContext: ProjectContext)
  extends SyntheticNamedElement(name) with ScFun {

  private var containingSyntheticClass: Option[ScSyntheticClass] = None

  def setContainingSyntheticClass(value: ScSyntheticClass): Unit = {
    assert(containingSyntheticClass.isEmpty, s"Containing synthetic class was already assigned to method $name")
    containingSyntheticClass = Some(value)
  }

  def isStringPlusMethod: Boolean = {
    if (name != "+") return false
    retType.extractClass match {
      case Some(clazz) => clazz.qualifiedName == "java.lang.String"
      case _ => false
    }
  }

  def this(name: String, retType: ScType, paramTypes: Seq[Seq[ScType]], paramsByName: Boolean = false)
          (implicit ctx: ProjectContext) =
    this(
      name = name,
      retType = retType,
      paramClauses = paramTypes.mapWithIndex { case (p, index) =>
        p.map(Parameter(_, isRepeated = false, isByName = paramsByName, index = index))
      },
      typeParameterNames = Nil
    )

  val typeParams: Seq[ScSyntheticTypeParameter] =
    typeParameterNames.map { name => new ScSyntheticTypeParameter(name, this) }
  override def typeParameters: Seq[ScTypeParam] = typeParams

  override def getIcon(flags: Int): Icon = Icons.FUNCTION

  override def toString = "Synthetic method"

  override protected def findChildrenByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): Array[T] = {
    findChildrenByClass[T](clazz)
  }

  override protected def findChildByClassScala[T >: Null <: ScalaPsiElement](clazz: Class[T]): T = {
    var cur: PsiElement = getFirstChild
    while (cur != null) {
      if (clazz.isInstance(cur)) return cur.asInstanceOf[T]
      cur = cur.getNextSibling
    }
    null
  }

  override def getNavigationElement: PsiElement = cachedInUserData("ScSyntheticFunction.getNavigationElement", this, ProjectRootManager.getInstance(projectContext.project)) {
    val syntheticFunctionSourceMirror = containingSyntheticClass.flatMap(_.getNavigationElement match {
      case classInSources: ScTemplateDefinition =>
        //NOTE: we search for the function with the same name ignoring overloaded functions
        // in principle this is not entirely correct, but for the synthetic classes in the Scala library
        // it should work fine because it's known that there are no overloaded methods in those classes
        classInSources.members.filterByType[ScFunction].find(_.name == name)
      case _ => None
    })
    syntheticFunctionSourceMirror.getOrElse(super.getNavigationElement)
  }
}

@Service(Array(Service.Level.PROJECT))
final class SyntheticClasses(project: Project) {
  implicit def ctx: ProjectContext = project

  private[synthetic] def clear(): Unit = {
    if (classesInitialized) {
      sharedClasses.clear()
      scala3Classes.clear()
      anyValCompanionObjects.clear()
      aliases.clear()
    }

    stringPlusMethod = null
    file = null
  }

  @volatile
  private var classesInitialized: Boolean = false

  def isClassesRegistered: Boolean = classesInitialized

  var stringPlusMethod: ScType => ScSyntheticFunction = _

  private val sharedClasses: mutable.Map[String, PsiClass] = mutable.HashMap.empty[String, PsiClass]
  private val scala3Classes: mutable.Map[String, PsiClass] = mutable.HashMap.empty[String, PsiClass]
  val aliases: mutable.Set[ScTypeAlias]      = mutable.HashSet.empty[ScTypeAlias]

  private val anyValCompanionObjects: mutable.Map[String, ScObject] = mutable.HashMap.empty

  private[synthetic]
  var file : PsiFile = _

  def registerClasses(): Unit = {
    val stdTypes = ctx.stdTypes
    import stdTypes._
    val typeParameters = SyntheticClasses.TypeParameter :: Nil

    val fileName = s"dummy-synthetics.scala"
    val emptyScalaFile = PsiFileFactory.getInstance(project).createFileFromText(fileName, ScalaFileType.INSTANCE, "")
    file = emptyScalaFile

    //
    // Scala 2 library bootstrap classes, which are not present in the `.class` files
    // https://github.com/scala/scala/tree/2.13.x/src/library-aux/scala
    //
    val any = registerClass(Any, "Any")
    any.addMethod(new ScSyntheticFunction("==", Boolean, Seq(Seq(Any))))
    any.addMethod(new ScSyntheticFunction("!=", Boolean, Seq(Seq(Any))))
    any.addMethod(new ScSyntheticFunction("##", Int, Nil))
    any.addMethod(new ScSyntheticFunction("isInstanceOf", Boolean, Nil, typeParameters))
    any.addMethod(new ScSyntheticFunction("asInstanceOf", Any, Nil, typeParameters) {
      override val retType: ScType = TypeParameterType(typeParams.head)
    })

    val anyRef = registerClass(AnyRef, "AnyRef")
    anyRef.addMethod(new ScSyntheticFunction("eq", Boolean, Seq(Seq(AnyRef))))
    anyRef.addMethod(new ScSyntheticFunction("ne", Boolean, Seq(Seq(AnyRef))))
    anyRef.addMethod(new ScSyntheticFunction("synchronized", Any, Nil, typeParameters) {
      override val paramClauses: Seq[Seq[Parameter]] = Seq(Seq(Parameter(
        TypeParameterType(typeParams.head), isRepeated = false, index = 0)))
      override val retType: ScType = TypeParameterType(typeParams.head)
    })

    registerClass(AnyVal, "AnyVal")
    registerClass(Nothing, "Nothing")
    registerClass(Null, "Null")
    registerClass(Singleton, "Singleton")
    registerClass(Unit, "Unit")

    stringPlusMethod = new ScSyntheticFunction("+", _, Seq(Seq(Any)))

    def createDummyFile(debugName: String, fileText: String) = {
      val fileName = s"dummy-synthetic-$debugName.scala"
      PsiFileFactory
        .getInstance(project)
        .createFileFromText(fileName, ScalaFileType.INSTANCE, fileText)
        .asInstanceOf[ScalaFile]
    }

    def toStdType(te: ScTypeElement): StdType =
      stdTypes.QualNameToType.getOrElse("scala." + te.getText, throw new AssertionError("Cant find StdType $qualifiedTypeText"))

    def toStdTypeOpt(te: ScTypeElement): Option[StdType] =
      stdTypes.QualNameToType.get("scala." + te.getText)

    //It only handles function which operates with StdTypes
    def convertToSyntheticFunction(f: ScFunction, containingClassName: String): Option[ScSyntheticFunction] = {
      //NOTE: for the "toString" function `toStdTypeOpt` will return None,
      // and we will not register a synthetic function
      // because String is not a StdType and we can't refer to it here
      f.returnTypeElement.flatMap(toStdTypeOpt).map(rt => {
        val paramTypes = f.paramClauses.clauses.map(_.parameters.map(_.typeElement.getOrElse {
          throw new AssertionError(s"Can't find parameter type element for parameter in function ${f.name}")
        }).map(toStdType))

        val functionName = f.name
        //NOTE: for Boolean, the effective signature of `def ||(x: Boolean): Boolean` (or same with &&)
        // is `def ||(x: => Boolean): Boolean`, but Boolean.scala sources contain the first variant due to some compiler limitations
        val paramsByName = containingClassName == "Boolean" && (functionName == "&&" || functionName == "||")
        new ScSyntheticFunction(functionName, rt, paramTypes, paramsByName = paramsByName)
      })
    }

    def convertToSyntheticClass(cls: ScClass, typ: StdType): ScSyntheticClass = {
      val className = cls.name
      val syntheticClass = new ScSyntheticClass(className, typ)

      val functions = cls.members.filterByType[ScFunction]
      val syntheticFunctions = functions.flatMap(convertToSyntheticFunction(_, className))
      syntheticFunctions.foreach(syntheticClass.addMethod)

      syntheticClass
    }

    def registerAnyValClasses(): Unit = {
      val classLoader = this.getClass.getClassLoader

      stdTypes.allAnyValTypes.foreach { typ =>
        val resourcePath = s"scalaLibraryAnyValTypesSources/${typ.name}.scala"
        Option(classLoader.getResourceAsStream(resourcePath)).foreach { stream =>
          val fileText = scala.io.Source.fromInputStream(stream).mkString
          val dummyFile = createDummyFile(fileName.toLowerCase, fileText)

          val classes = dummyFile.typeDefinitions.filterByType[ScClass]
          val objects = dummyFile.typeDefinitions.filterByType[ScObject]

          val syntheticClasses: Seq[ScSyntheticClass] = classes.map(convertToSyntheticClass(_, typ))
          syntheticClasses.foreach { cls =>
            sharedClasses.put(cls.name, cls)
          }
          objects.foreach { obj =>
            anyValCompanionObjects.put(obj.name, obj)
          }
        }
      }
    }

    registerAnyValClasses()

    def registerContextFunctionClass(debugName: String, fileText: String): Unit = {
      val dummyFile = createDummyFile(debugName, fileText)
      val cls = dummyFile.typeDefinitions.head.asInstanceOf[PsiClass]
      scala3Classes.put(cls.name, cls)
    }

    (1 to 22).foreach { n =>
      val typeParameters    = (1 to n).map(i => s"-T$i").mkString(", ")
      val contextParameters = (1 to n).map(i => s"x$i: T$i").mkString(", ")

      registerContextFunctionClass("ContextFunction",
        s"""
           |package scala
           |
           |trait ContextFunction$n[$typeParameters, +R] {
           |  def apply(implicit $contextParameters): R
           |}
           |""".stripMargin
      )
    }

    def registerAlias(@Language("Scala") text: String): Unit = {
      val file  = ScalaPsiElementFactory.createScalaFileFromText(text, ScalaFeatures.default)
      val alias = file.members.head.asInstanceOf[ScTypeAlias]
      aliases += alias
    }

    //
    // Scala 3 library
    //
    // - https://www.scala-lang.org/api/3.0.2/scala/AnyKind.html
    // - https://dotty.epfl.ch/docs/reference/other-new-features/kind-polymorphism.html
    registerClass(AnyKind, "AnyKind", isScala3 = true)
    registerAlias(
      """package scala
        |
        |type &[A, B]
        |""".stripMargin
    )
    registerAlias(
      """package scala
        |
        |type |[A, B]
        |""".stripMargin
    )

    classesInitialized = true
  }

  def registerClass(t: StdType, name: String, isScala3: Boolean = false): ScSyntheticClass = {
    val cls = new ScSyntheticClass(name, t)

    if (isScala3)
      scala3Classes += ((name, cls))
    else
      sharedClasses += ((name, cls))

    cls
  }

  def allClasses(): Iterable[PsiClass] =
    sharedClasses.values ++ scala3Classes.values

  def allElements(shouldProcessScala3Definitions: Boolean): Iterable[PsiNamedElement] = {
    val scala3Elements = if (shouldProcessScala3Definitions) scala3Classes.values ++ aliases.iterator else Iterator.empty
    sharedClasses.values ++ anyValCompanionObjects.valuesIterator ++ scala3Elements
  }

  @TestOnly
  def sharedClassesOnly: Iterable[PsiClass] = sharedClasses.values

  @TestOnly
  def scala3ClassesOnly: Iterable[PsiClass] = scala3Classes.values

  def byName(name: String): Option[PsiClass] = sharedClasses.get(name).orElse(scala3Classes.get(name))

  val prefix = "scala."

  def findClass(qName: String): PsiClass = {
    if (qName.startsWith(prefix)) {
      byName(qName.substring(prefix.length)) match {
        case Some(c) => return c
        case _ =>
      }
    }
    anyValCompanionObjects.get(qName).orNull
  }

  def findClasses(qName: String): Array[PsiClass] = {
    val c = findClass(qName)
    val obj = anyValCompanionObjects.get(qName).orNull

    if (c != null && obj != null && c != obj)
      Array(c, obj)
    else if (c != null)
      Array(c)
    else
      Array.empty
  }
}

final class SyntheticClassElementFinder(project: Project) extends PsiElementFinder {
  private[this] val instance = SyntheticClasses.get(project)

  override def findClass(
    qualifiedName: String,
    scope:         GlobalSearchScope
  ): PsiClass = instance.findClass(qualifiedName)

  override def findClasses(
    qualifiedName: String,
    scope:         GlobalSearchScope
  ): Array[PsiClass] = instance.findClasses(qualifiedName)
}

object SyntheticClasses {
  def get(project: Project): SyntheticClasses = project.getService(classOf[SyntheticClasses])

  val TypeParameter = "TypeParameterForSyntheticFunction"
}

final class RegisterSyntheticClassesStartupActivity extends StartupActivity.DumbAware {
  override def runActivity(project: Project): Unit = {
    // NOTE: run `registerClasses` on EDT!
    // Don't use `inReadAction`, it can significantly increase setup time for each test case.
    // Details: DumbAware startup activity is run on the background thread.
    // Under the hood `registerClasses` involves parsing of registered synthetic classes.
    // This parsing can take a long time, if it's done on a background thread.
    // This is because, during the parsing `ProgressManager.checkCanceled()` is being frequently called.
    // And it calls `CoreProgressManager.sleepIfNeededToGivePriorityToAnotherThread`.
    // This method causes the parsing thread to sleep 1s each time there is some other thread with a higher priority.
    // This can increase light test project initialization from 0.1s to 12s on Windows (!)
    invokeLater(SyntheticClasses.get(project).registerClasses())
  }
}

class DeregisterSyntheticClassesListener extends ProjectManagerListener {
  override def projectClosing(project: Project): Unit = {
    SyntheticClasses.get(project).clear()
  }
}

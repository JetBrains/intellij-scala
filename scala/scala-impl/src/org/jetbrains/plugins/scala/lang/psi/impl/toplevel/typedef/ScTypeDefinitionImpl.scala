package org.jetbrains.plugins.scala.lang.psi.impl.toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.navigation._
import com.intellij.psi._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker, cached, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiImplementationHelper
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.stubOrPsiNextSibling
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCases, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createObjectWithContext
import org.jetbrains.plugins.scala.lang.psi.light.ScFunctionWrapper
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType.ImplicitValueClassDumbMode
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.BacktickedName.stripBackticks
import org.jetbrains.plugins.scala.projectView.FileKind
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants
import org.jetbrains.plugins.scala.util.ScalaBytecodeConstants.{PackageObjectClassName, PackageObjectClassPackageSuffix, PackageObjectSingletonClassPackageSuffix}

import javax.swing.Icon
import scala.annotation.tailrec

abstract class ScTypeDefinitionImpl[T <: ScTemplateDefinition](stub: ScTemplateDefinitionStub[T],
                                                               nodeType: ScTemplateDefinitionElementType[T],
                                                               node: ASTNode,
                                                               debugName: String)
  extends ScTemplateDefinitionImpl(stub, nodeType, node, debugName)
    with ScTypeDefinition
    with ScTypeDefinitionLikeImpl {

  override def hasTypeParameters: Boolean = typeParameters.nonEmpty

  override def typeParameters: Seq[ScTypeParam] =
    super.typeParameters

  override def add(element: PsiElement): PsiElement = element match {
    case member: ScMember =>
      addMember(member, None)
    case _ =>
      super.add(element)
  }

  // For Scala PSI
  override def annotationType: Boolean =
    elementScope.getCachedClass("scala.annotation.Annotation")
      .exists(isInheritor(_, checkDeep = true))

  // For Java PSI
  override def isAnnotationType: Boolean = false

  override final def `type`(): TypeResult = getTypeWithProjections(thisProjections = true)

  override final def getTypeWithProjections(thisProjections: Boolean): TypeResult = {
    val designator = containingClass match {
      case null => ScalaType.designator(this)
      case clazz =>
        val projected = if (thisProjections) ScThisType(clazz)
        else clazz.getTypeWithProjections().getOrElse {
          return Failure(ScalaBundle.message("cannot.resolve.parent.class"))
        }

        ScProjectionType(projected, this)
    }

    val result =
      if (typeParameters.isEmpty) designator
      else                        ScParameterizedType(designator, typeParameters.map(TypeParameterType(_)))

    Right(result)
  }

  override def getModifierList: ScModifierList =
    super[ScTypeDefinition].getModifierList

  // TODO Should be unified, see ScModifierListOwner
  override def hasModifierProperty(name: String): Boolean =
    super[ScTypeDefinition].hasModifierProperty(name)

  override def getNavigationElement: PsiElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorClass
    case _ => this
  }

  private def hasSameScalaKind(other: PsiClass) = (this, other) match {
    case (_: ScTrait, _: ScTrait) |
         (_: ScObject, _: ScObject) |
         (_: ScClass, _: ScClass) |
         (_: ScGivenDefinition, _: ScGivenDefinition) => true
    case _ => false
  }

  override def getSourceMirrorClass: PsiClass = {
    val classParent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition], true)
    val name = this.name
    if (classParent == null) {
      val containingFile = getContainingFile
      val fileNavigationElement = containingFile.getNavigationElement
      val classes: Array[PsiClass] = fileNavigationElement match {
        case o: ScalaFile => o.typeDefinitions.toArray
        case o: PsiClassOwner => o.getClasses
      }
      val classesIterator = classes.iterator
      while (classesIterator.hasNext) {
        val c = classesIterator.next()
        val className = c.name
        val matches = name == className && hasSameScalaKind(c) || (c match {
          //A "legacy" package object has the name `package`.
          //However, when we decompile its class file, we use the package name.
          //Thus, the condition "myName == className" returns false.
          //Real world example: `scala.sys.process` package object in scala 2.13 (yes, they use "legacy" naming for some reason)
          case o: ScObject => o.isPackageObjectLegacy && this.isPackageObject
          case _ => false
        })
        if (matches)
          return c
      }
    } else {
      val parentSourceMirror = classParent.asInstanceOf[ScTypeDefinitionImpl[_]].getSourceMirrorClass
      parentSourceMirror match {
        case td: ScTypeDefinitionImpl[_] =>
          for (i <- td.typeDefinitions if name == i.name && hasSameScalaKind(i))
            return i
        case _ =>
      }
    }
    this
  }

  override def isLocal: Boolean =
    byStubOrPsi(stub => stub.isLocal && !stub.isTopLevel) {
      super.isLocal && PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition]) != null
    }

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def getContainingClass: PsiClass = {
    super[ScTypeDefinition].getContainingClass match {
      case o: ScObject => o.fakeCompanionClassOrCompanionClass
      case containingClass => containingClass
    }
  }

  override def fakeCompanionModule: Option[ScObject] = this match {
    case ImplicitValueClassDumbMode(c) if !c.qualifiedName.startsWith("scala.Predef.") => calcFakeCompanionModule(true)
    case _: ScObject => None
    case _ =>
      baseCompanion match {
        case Some(_: ScObject)                                              => None
        case _ if !isCase && !SyntheticMembersInjector.needsCompanion(this) => None
        case _                                                              => calcFakeCompanionModule(false)
      }
    }

  private val calcFakeCompanionModule: Boolean => Option[ScObject] = cached("calcCompanionModule", ModTracker.libraryAware(this), (isImplicitValueClass: Boolean) => {
    val accessModifier = getModifierList.accessModifier match {
      case None     => ""
      case Some(am) => AccessModifierRenderer.simpleTextHtmlEscaped(am) + " "
    }

    val packageDollar = this.containingClass match {
      case o: ScObject if o.isPackageObject && isImplicitValueClass =>
        ScalaBytecodeConstants.PackageObjectSingletonClassName
      case _ => ""
    }

    val objText =
      s"""${accessModifier}object $packageDollar$name {
         |  //Generated synthetic object
         |}""".stripMargin

    val child = stubOrPsiNextSibling(this) match {
      case null => this
      case next => next
    }

    createObjectWithContext(objText, getContext, child) match {
      case null => None
      case obj =>
        obj.isSyntheticObject = true
        obj.syntheticNavigationElement = this
        Some(obj)
    }
  })


  import ScTypeDefinitionImpl._

  override final def getQualifiedName: String = _getQualifiedName()

  private val _getQualifiedName = cached("getQualifiedName", ModTracker.anyScalaPsiChange, () => {
    //NOTE: according to `getQualifiedName` contract
    //null should be returned for anonymous and local classes (and for type parameters, but it's not relevant here?)
    //Related: SCL-15357, KTIJ-24653
    if (isLocalOrInsideAnonymous(this))
      null
    else {
      byStubOrPsi(_.javaQualifiedName) {
        val suffix = this match {
          case o: ScObject if o.isPackageObject => PackageObjectSingletonClassPackageSuffix
          case _: ScObject => "$"
          case _ => ""
        }

        import ScalaNamesUtil.{BacktickedName, toJavaName}
        val fqn = qualifiedName(DefaultSeparator, forJvmRepresentation = true)(toJavaName)
          .split('.')
          .map(BacktickedName.stripBackticks)
          .mkString(DefaultSeparator)

        //We need to handle legacy package object in order we can access it from java like this:
        // `org.legacy.package$.MODULE$.fooStringType()`
        //Without this handling the jvm qualified name will be `org.legacy.package.package$` which is wrong
        val result = fqn.stripSuffix(ScObjectImpl.LegacyPackageObjectPackageSuffix) + suffix
        result
      }
    }
  })

  override def qualifiedName: String = _qualifiedName()

  private val _qualifiedName = cached("qualifiedName", ModTracker.anyScalaPsiChange, () => {
    if (isLocalOrInsideAnonymous(this)) name
    else byStubOrPsi(_.getQualifiedName) {
      qualifiedName(DefaultSeparator, forJvmRepresentation = false)(identity)
    }
  })

  /**
   * Returns the resolved effective direct parents of this definition.
   *
   * Differences between other methods:
   *  - Unlike [[getSuperTypes]], this result does not retain generic arguments or substitutions and omits unresolved parents.
   *  - Unlike [[getExtendsListTypes]] and [[getImplementsListTypes]], it is not split into Java `extends` and
   *    `implements` roles: it includes every effective parent, such as the implicit root and synthetic case-class parents.
   *
   * Synthetic parents are included when they resolve to a [[PsiClass]]; unresolved synthetic parents are omitted
   * together with other unresolved parents.
   *
   * `java.lang.Object` is returned when it is the effective root (there is no class parent, or the parent is `Any`
   * or `AnyRef`) and it resolves. It is not added beside another concrete class parent or in place of `AnyVal`.
   */
  override def getSupers: Array[PsiClass] = {
    val supers = extendsBlock.supers
    supers.filter(_ != this).toArray
  }

  /**
   * Returns the effective direct parent types of this definition, retaining generic arguments and substitutions.
   *
   * Differences between other methods:
   *  - Unlike [[getSupers]], this result keeps the richer [[PsiClassType]] representation.
   *  - Unlike [[getExtendsListTypes]] and [[getImplementsListTypes]], it is a complete effective hierarchy view: it
   *    includes synthetic parents and the implicit root rather than only the Java-facing parent-list roles.
   *
   * Synthetic parents are included when their Scala type can be represented as a [[PsiClassType]].
   *
   * `java.lang.Object` is included as the effective root when there is no other class parent, including for
   * `Any`, `AnyRef`, and the Java projection of `AnyVal`; it is not appended beside another class parent.
   *
   * TODO: Deduplicate Java-equivalent super types separately; value classes currently expose java.lang.Object twice.
   */
  override def getSuperTypes: Array[PsiClassType] = toPsiClassTypes(superTypes)

  /**
   * Returns direct parents in the Java-facing `extends` role.
   *
   * Differences between other methods:
   *  - For class-like definitions, unlike [[getImplementsListTypes]], this result contains only non-interface parents.
   *    A Scala trait is a Java interface, so its direct parents are returned here instead, as Java-interface parents are.
   *  - Unlike [[getSuperTypes]], it is limited to PSI-backed template parents and does not expose the ordinary
   *    implicit root or synthetic interface parents.
   *
   * Synthetic class parents represented by template parent elements are included. Synthetic interfaces belong to
   * [[getImplementsListTypes]] for class-like definitions; traits do not implement interfaces.
   *
   * `java.lang.Object` is returned only when an explicit PSI-backed parent projects to it, such as `Any`, `AnyRef`,
   * `AnyVal`, or `java.lang.Object`; the ordinary implicit root is not added.
   */
  override def getExtendsListTypes: Array[PsiClassType] = getExtendsOrImplementsListTypes(forImplementsList = false)

  /**
   * Returns effective direct interface parents in the Java-facing `implements` role.
   *
   * Differences between other methods:
   *  - Unlike [[getExtendsListTypes]], this result contains direct interface parents only for class-like definitions,
   *    including synthetic interfaces such as `Product` and `Serializable` for case classes. A trait's direct
   *    interface parents belong to [[getExtendsListTypes]], as they do for a Java interface.
   *  - Unlike [[getSuperTypes]], it excludes class parents and the implicit root.
   *
   * Synthetic interfaces, including case-class parents and injected interfaces, are included; synthetic class
   * parents are excluded.
   *
   * `java.lang.Object` is never returned because it is a class parent, not an interface parent.
   */
  override def getImplementsListTypes: Array[PsiClassType] = getExtendsOrImplementsListTypes(forImplementsList = true)

  override def getQualifiedNameForDebugger: String = {
    import ScalaNamesUtil.toJavaName
    containingClass match {
      case td: ScTypeDefinition =>
        td.getQualifiedNameForDebugger + "$" + toJavaName(name)
      case _ if isPackageObject =>
        qualifiedName("", forJvmRepresentation = true)(toJavaName) + PackageObjectClassPackageSuffix
      case _ =>
        qualifiedName("$", forJvmRepresentation = true)(s => toJavaName(stripBackticks(s)))
    }
  }

  protected def qualifiedName(
    separator: String,
    forJvmRepresentation: Boolean
  )(nameTransformer: String => String): String = {
    val packageName = getPackageName(
      this,
      separator,
      forJvmRepresentation,
      Right(this) :: Nil
    )
    toQualifiedName(packageName)(nameTransformer)
  }

  override def getPresentation: ItemPresentation = {
    val presentableName = this match {
      case o: ScObject if o.isPackageObjectLegacy =>
        val packageName = ScObjectImpl.stripLegacyPackageObjectSuffixWithDot(o.qualifiedName)
        val index = packageName.lastIndexOf('.')
        if (index < 0) packageName else packageName.substring(index + 1, packageName.length)
      case _ => name
    }

    new ItemPresentation() {
      override def getPresentableText: String = presentableName

      override def getLocationString: String = getPath match {
        case "" => ScTypeDefinitionImpl.DefaultLocationString
        case path => path.parenthesize()
      }

      override def getIcon(open: Boolean): Icon = ScTypeDefinitionImpl.this.getIcon(0)
    }
  }

  override def delete(): Unit = {
    val containingFile = getContainingFile
    val deleteWholeFile = isTopLevel && (containingFile match {
      case scalaFile: ScalaFile =>
        val fileKind = FileKind.getForFile(scalaFile)
        fileKind.isDefined
      case _ =>
        false
    })
    if (deleteWholeFile) {
      containingFile.delete()
    }
    else {
      getParent.getNode.removeChild(getNode)
    }
  }

  override def psiTypeParameters: Array[PsiTypeParameter] = typeParameters.makeArray(PsiTypeParameter.ARRAY_FACTORY)

  override def methodsByName(name: String): Iterator[PhysicalMethodSignature] = {
    TypeDefinitionMembers.getSignatures(this).forName(name)
      .iterator
      .collect {
        case p: PhysicalMethodSignature => p
      }
  }

  override def getDocComment: PsiDocComment =
    super[ScTypeDefinition].getDocComment

  override def isDeprecated: Boolean = byStubOrPsi(_.isDeprecated)(super.isDeprecated)

  override def psiInnerClasses: Array[PsiClass] = {
    val inCompanionModule = baseCompanion.toSeq.flatMap {
      case o: ScObject =>
        o.membersWithSynthetic.flatMap {
          case o: ScObject => Seq(o) ++ o.fakeCompanionClass
          case t: ScTrait => Seq(t, t.fakeCompanionClass)
          case c: ScClass => Seq(c)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }

    (this.membersWithSynthetic.collect {
      case c: PsiClass => c
    } ++ inCompanionModule).toArray
  }

  override def getOriginalElement: PsiElement =
    ScalaPsiImplementationHelper.getOriginalClass(this)

  override def syntheticTypeDefinitions: Seq[ScTypeDefinition] =
    cachedWithRecursionGuard("syntheticTypeDefinitions", this, Seq.empty[ScTypeDefinition], BlockModificationTracker(this)) {
      SyntheticMembersInjector.injectInners(this)
    }

  override def syntheticMembers: Seq[ScMember] =
    cachedWithRecursionGuard("syntheticMembers", this, Seq.empty[ScMember], BlockModificationTracker(this)) {
      SyntheticMembersInjector.injectMembers(this)
    }

  override def syntheticMethods: Seq[ScFunction] =
    cachedWithRecursionGuard("syntheticMethods", this, Seq.empty[ScFunction], BlockModificationTracker(this)) {
      SyntheticMembersInjector.inject(this)
    }

  override def psiMethods: Array[PsiMethod] =
    cachedWithRecursionGuard("psiMethods", this, PsiMethod.EMPTY_ARRAY, ModTracker.libraryAware(this)) {
      // note: this could be optimized my not processing super classes as it's unnecessary
      val allMethods = getAllMethods
      // Filter out mixed-in methods
      // It was primarily added for com.intellij.psi.impl.search.JavaOverridingMethodsSearcher.findOverridingMethod
      // Note, exported members are considered as implementations as they have a physical export statement in the code.
      //
      // NOTE: Similar filtering is done for a Scala version of the searcher in ScalaOverridingMemberSearcher.processImpl
      // It's not a direct alternative, but still it's related.
      val methodsFiltered = allMethods.filter(m => m.containingClass == this && !isMixedInSyntheticMethod(m))
      methodsFiltered
    }

  /**
   * @return true - if the method represents a method in a class that was mixed-in from a trait and does not exist in the class sources
   *         (see scaladoc of [[ScFunctionWrapper.isMixedInSyntheticMethod]]).
   * @note ATTENTION: This might lead to some inconsistencies and confusions in other parts of IDE features:
   *       the containing class of such methods can be equal to this class (reflecting how it will be in the JVM bytecode).
   *       But we filter them out from `getMethods` / `psiMethods` (reflecting how it is in the Scala sources).
   */
  private def isMixedInSyntheticMethod(method: PsiMethod): Boolean = method match {
    case wrapper: ScFunctionWrapper =>
      wrapper.isMixedInSyntheticMethod
    case _ =>
      false
  }
}

object ScTypeDefinitionImpl {

  private type QualifiedNameList = List[Either[String, ScTypeDefinition]]

  val DefaultSeparator = "."

  val DefaultLocationString = "<default>"

  /**
   * Returns prefix with a convenient separator
   *
   * @param forJvmRepresentation when true, return package name in JVM representation
   *                             For example, for a package object it returns `org.example.package` instead of `org.example
   */
  def getPackageName(
    element: PsiElement,
    separator: String,
    forJvmRepresentation: Boolean,
    builder: QualifiedNameList = Nil,
  ): QualifiedNameList = {
    @tailrec
    def inner(element: PsiElement, acc: QualifiedNameList): QualifiedNameList = element.getContext match {
      case packageObject: ScObject if packageObject.isPackageObjectNonLegacy =>
        //NOTE: in JVM bytecode scala package object is represented by a class with name "package"
        //Even though it's not possible to reference it in Java (because "package" is a keyword)
        // you can still reference it from Scala or Kotlin using backticks (``).
        //For example this Scala code:
        //```scala
        //  package org
        //  package object example { class Inner }
        //```
        //class "Inner" can be referenced from Kotlin using org.example.`package`.Inner
        //it can be reference from scala using both org.example.Inner and org.example.`package`.Inner
        //however the latter is considered an implementation detail and generally shouldn't be used in scala sources
        val newAcc =  if (forJvmRepresentation)
          Right(packageObject) :: Left(separator) :: Left(PackageObjectClassName) :: Left(separator) :: acc
        else
          Right(packageObject) :: Left(separator) :: acc
        inner(packageObject, newAcc)

      case packageObject: ScObject if packageObject.isPackageObjectLegacy =>
        val newAcc = if (forJvmRepresentation)
          Left(PackageObjectClassName) :: Left(separator) :: acc
        else
          acc
        inner(packageObject, newAcc)

      case definition: ScTypeDefinition =>
        inner(
          definition,
          Right(definition) :: Left(separator) :: acc
        )
      case packaging: ScPackaging =>
        val packageNamesList = packaging.fullPackageName
          .split('.').toSeq
          .intersperse(".")
          .map(Left(_))
          .toList
        packageNamesList ::: Left(".") :: acc
      case _: ScalaFile |
           _: PsiFile |
           _: ScBlock |
           null =>
        acc
      case context@(_: ScTemplateBody |
                    _: ScEnumCases    |
                    _: ScExtendsBlock |
                    _: ScTemplateParents) =>
        inner(context, acc)
      case context =>
        inner(context, Nil)
    }

    inner(element, builder)
  }

  def toQualifiedName(list: QualifiedNameList)
                     (nameTransformer: String => String = identity): String = list.map {
    case Right(definition) => nameTransformer(definition.name)
    case Left(".") => "."
    case Left(string) => nameTransformer(string)
  }.mkString

  private def isLocalOrInsideAnonymous(td: ScTypeDefinition): Boolean =
    td.isLocal || PsiTreeUtil.getStubOrPsiParentOfType(td, classOf[ScNewTemplateDefinition]) != null
}

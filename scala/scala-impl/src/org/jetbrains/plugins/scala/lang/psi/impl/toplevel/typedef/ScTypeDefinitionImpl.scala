package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

/**
 * @author ilyas
 */

import com.intellij.lang.ASTNode
import com.intellij.navigation._
import com.intellij.psi._
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.Icon
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScTypeDefinitionFactory
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, ModTracker}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets.TYPE_DEFINITIONS
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.stubOrPsiNextSibling
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScNewTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createObjectWithContext
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.AccessModifierRenderer
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, CachedInUserData, CachedWithRecursionGuard}
import org.jetbrains.plugins.scala.projectView.FileKind

import scala.annotation.tailrec

abstract class ScTypeDefinitionImpl[T <: ScTemplateDefinition](stub: ScTemplateDefinitionStub[T],
                                                               nodeType: ScTemplateDefinitionElementType[T],
                                                               node: ASTNode,
                                                               debugName: String)
  extends ScTemplateDefinitionImpl(stub, nodeType, node, debugName)
    with ScTypeDefinition {

  override def hasTypeParameters: Boolean = typeParameters.nonEmpty

  override def typeParameters: Seq[ScTypeParam] = desugaredElement match {
    case Some(td: ScTypeDefinition) => td.typeParameters
    case _ => super.typeParameters
  }

  override def add(element: PsiElement): PsiElement = element match {
    case member: ScMember => addMember(member, None)
      case _ => super.add(element)
  }

  override def getSuperTypes: Array[PsiClassType] =
    superTypes.map {
      _.toPsiType
    }.collect {
      case c: PsiClassType => c
    }.toArray

  override def isAnnotationType: Boolean =
    elementScope.getCachedClass("scala.annotation.Annotation")
      .exists(isInheritor(_, checkDeep = true))

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

    val result = typeParameters match {
      case typeArgs if typeArgs.isEmpty => designator
      case typeArgs => ScParameterizedType(designator, typeArgs.map(TypeParameterType(_)))
    }
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
    case (_: ScTrait, _: ScTrait)
            | (_: ScObject, _: ScObject)
            | (_: ScClass, _: ScClass) => true
    case _ => false
  }

  override def getSourceMirrorClass: PsiClass = {
    val classParent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition], true)
    val name = this.name
    if (classParent == null) {
      val classes: Array[PsiClass] = getContainingFile.getNavigationElement match {
        case o: ScalaFile => o.typeDefinitions.toArray
        case o: PsiClassOwner => o.getClasses
      }
      val classesIterator = classes.iterator
      while (classesIterator.hasNext) {
        val c = classesIterator.next()
        if (name == c.name && hasSameScalaKind(c)) return c
      }
    } else {
      val parentSourceMirror = classParent.asInstanceOf[ScTypeDefinitionImpl[_]].getSourceMirrorClass
      parentSourceMirror match {
        case td: ScTypeDefinitionImpl[_] => for (i <- td.typeDefinitions if name == i.name && hasSameScalaKind(i))
          return i
        case _ => this
      }
    }
    this
  }

  override def isLocal: Boolean =
    byStubOrPsi(_.isLocal) {
      super.isLocal && PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition]) != null
    }

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def getTextOffset: Int =
    nameId.getTextRange.getStartOffset

  override def getContainingClass: PsiClass = {
    super[ScTypeDefinition].getContainingClass match {
      case o: ScObject => o.fakeCompanionClassOrCompanionClass
      case containingClass => containingClass
    }
  }

  //Performance critical method
  //And it is REALLY SO!
  final override def baseCompanionModule: Option[ScTypeDefinition] = {
    val isObject = this match {
      case _: ScObject => true
      case _: ScTrait | _: ScClass => false
      case _ => return None
    }

    val thisName: String = name

    val sameElementInContext = this.getSameElementInContext

    sameElementInContext match {
      case td: ScTypeDefinition if isCompanion(td) => return Some(td)
      case _ =>
    }

    def isCompanion(td: ScTypeDefinition): Boolean = td match {
      case td @ (_: ScClass | _: ScTrait)
        if isObject && td.name == thisName => true
      case o: ScObject if !isObject && thisName == o.name => true
      case _ => false
    }

    def findByStub(contextStub: StubElement[_]): Option[ScTypeDefinition] = {
      val siblings  = contextStub.getChildrenByType(TYPE_DEFINITIONS, ScTypeDefinitionFactory)
      siblings.find(isCompanion)
    }

    def findByAst: Option[ScTypeDefinition] = {

      var sibling: PsiElement = sameElementInContext

      while (sibling != null) {

        sibling = sibling.getNextSibling

        sibling match {
          case td: ScTypeDefinition if isCompanion(td) => return Some(td)
          case _ =>
        }
      }

      sibling = sameElementInContext
      while (sibling != null) {

        sibling = sibling.getPrevSibling

        sibling match {
          case td: ScTypeDefinition if isCompanion(td) => return Some(td)
          case _ =>
        }
      }

      None
    }

    val contextStub = getContext match {
      case stub: ScalaStubBasedElementImpl[_, _] => stub.getStub
      case file: PsiFileImpl => file.getStub
      case _ => null
    }

    if (contextStub != null) findByStub(contextStub)
    else findByAst
  }


  override def fakeCompanionModule: Option[ScObject] = this match {
    case _: ScObject => None
    case enm: ScEnum => enm.syntheticClass.flatMap(_.fakeCompanionModule)
    case _ =>
      baseCompanionModule match {
        case Some(_: ScObject)                                              => None
        case _ if !isCase && !SyntheticMembersInjector.needsCompanion(this) => None
        case _                                                              => calcFakeCompanionModule()
      }
    }

  @Cached(ModTracker.libraryAware(this), this)
  private def calcFakeCompanionModule(): Option[ScObject] = {
    val accessModifier = getModifierList.accessModifier match {
      case None     => ""
      case Some(am) => AccessModifierRenderer.simpleTextHtmlEscaped(am) + " "
    }

    val objText =
      s"""${accessModifier}object $name {
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
  }


  import ScTypeDefinitionImpl._

  @Cached(ModTracker.anyScalaPsiChange, this)
  override final def getQualifiedName: String = {
    if (hasNoJavaFQName(this))
      return null

    byStubOrPsi(_.javaQualifiedName) {
      val suffix = this match {
        case o: ScObject if o.isPackageObject => ".package$"
        case _: ScObject => "$"
        case _ => ""
      }

      import ScalaNamesUtil.{isBacktickedName, toJavaName}
      val result = qualifiedName(DefaultSeparator)(toJavaName)
        .split('.')
        .map(isBacktickedName(_).orNull)
        .mkString(DefaultSeparator)

      result + suffix
    }
  }

  @Cached(ModTracker.anyScalaPsiChange, this)
  override def qualifiedName: String = {
    if (isLocalOrInsideAnonymous(this))
      return name

    byStubOrPsi(_.getQualifiedName) {
      qualifiedName(DefaultSeparator)(identity)
    }
  }

  override def getExtendsListTypes: Array[PsiClassType] = innerExtendsListTypes

  override def getImplementsListTypes: Array[PsiClassType] = innerExtendsListTypes

  override def getQualifiedNameForDebugger: String = {
    import ScalaNamesUtil.toJavaName
    containingClass match {
      case td: ScTypeDefinition => td.getQualifiedNameForDebugger + "$" + toJavaName(name)
      case _ if isPackageObject => qualifiedName("")(toJavaName) + ".package"
      case _ => qualifiedName("$")(toJavaName)
    }
  }

  protected def qualifiedName(separator: String)
                             (nameTransformer: String => String): String =
    toQualifiedName(packageName(this)(Right(this) :: Nil, separator))(nameTransformer)

  override def getPresentation: ItemPresentation = {
    val presentableName = this match {
      case o: ScObject if o.isPackageObject && o.name == "`package`" =>
        val packageName = o.qualifiedName.stripSuffix(".`package`")
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

  override def delete(): Unit = getContainingFile match {
    case file@FileKind(_) if isTopLevel => file.delete()
    case _ => getParent.getNode.removeChild(getNode)
  }

  override def psiTypeParameters: Array[PsiTypeParameter] = typeParameters.makeArray(PsiTypeParameter.ARRAY_FACTORY)

  override def getSupers: Array[PsiClass] = extendsBlock.supers.filter {
    _ != this
  }.toArray

  override def methodsByName(name: String): Iterator[PhysicalMethodSignature] = {
    TypeDefinitionMembers.getSignatures(this).forName(name)
      .iterator
      .collect {
        case p: PhysicalMethodSignature => p
      }
  }

  override def getNameIdentifier: PsiIdentifier = {
    Predef.assert(nameId != null, "Class hase null nameId. Class text: " + getText) //diagnostic for EA-20122
    new JavaIdentifier(nameId)
  }

  override def getDocComment: PsiDocComment =
    super[ScTypeDefinition].getDocComment

  override def isDeprecated: Boolean = byStubOrPsi(_.isDeprecated)(super.isDeprecated)

  override def psiInnerClasses: Array[PsiClass] = {
    val inCompanionModule = baseCompanionModule.toSeq.flatMap {
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

  @CachedWithRecursionGuard(this, Seq.empty, BlockModificationTracker(this))
  override def syntheticTypeDefinitions: Seq[ScTypeDefinition] = SyntheticMembersInjector.injectInners(this)

  @CachedWithRecursionGuard(this, Seq.empty, BlockModificationTracker(this))
  override def syntheticMembers: Seq[ScMember] = SyntheticMembersInjector.injectMembers(this)

  @CachedWithRecursionGuard(this, Seq.empty, BlockModificationTracker(this))
  override def syntheticMethods: Seq[ScFunction] = SyntheticMembersInjector.inject(this)

  @CachedWithRecursionGuard(this, PsiMethod.EMPTY_ARRAY, ModTracker.libraryAware(this))
  override def psiMethods: Array[PsiMethod] = getAllMethods.filter(_.containingClass == this)

  @CachedWithRecursionGuard(this, None, BlockModificationTracker(this))
  override protected def desugaredInner: Option[ScTemplateDefinition] = {
    def toPsi(tree: scala.meta.Tree): ScTemplateDefinition = {
      ScalaPsiElementFactory.createTemplateDefinitionFromText(tree.toString(), getContext, this)
        .setOriginal(actualElement = this)
    }

    import scala.meta.intellij.psi._
    import scala.meta.{Defn, Term}

    val defn = this.metaExpand match {
      case Right(templ: Defn.Class) => Some(templ)
      case Right(templ: Defn.Trait) => Some(templ)
      case Right(templ: Defn.Object) => Some(templ)
      case Right(Term.Block(Seq(templ: Defn.Class, _))) => Some(templ)
      case Right(Term.Block(Seq(templ: Defn.Trait, _))) => Some(templ)
      case Right(Term.Block(Seq(templ: Defn.Object, _))) => Some(templ)
      case _ => None
    }

    defn.map(toPsi)
  }
}

object ScTypeDefinitionImpl {

  private type QualifiedNameList = List[Either[String, ScTypeDefinition]]

  val DefaultSeparator = "."

  val DefaultLocationString = "<default>"

  /**
    * Returns prefix with a convenient separator
    */
  @tailrec
  def packageName(element: PsiElement)
                 (implicit builder: QualifiedNameList,
                  separator: String): QualifiedNameList = element.getContext match {
    case packageObject: ScObject if packageObject.isPackageObject && packageObject.name == "`package`" =>
      packageName(packageObject)
    case definition: ScTypeDefinition =>
      packageName(definition)(
        Right(definition) :: Left(separator) :: builder,
        separator
      )
    case packaging: ScPackaging =>
      packageName(packaging)(
        Left(packaging.packageName) :: Left(DefaultSeparator) :: builder,
        DefaultSeparator
      )
    case _: ScalaFile |
         _: PsiFile |
         _: ScBlock |
         null => builder
    case context@(_: ScTemplateBody |
                  _: ScExtendsBlock |
                  _: ScTemplateParents) =>
      packageName(context)
    case context => packageName(context)(Nil, separator)
  }

  def toQualifiedName(list: QualifiedNameList)
                     (nameTransformer: String => String = identity): String = list.map {
    case Right(definition) => nameTransformer(definition.name)
    case Left(string) => string
  }.mkString

  private def isLocalOrInsideAnonymous(td: ScTypeDefinition): Boolean =
    td.isLocal || PsiTreeUtil.getStubOrPsiParentOfType(td, classOf[ScNewTemplateDefinition]) != null

  private def isInPackageObject(td: ScTypeDefinition): Boolean =
    td.containingClass match {
      case o: ScObject => o.isPackageObject
      case _ => false
    }

  private def hasNoJavaFQName(td: ScTypeDefinition): Boolean = {
    isLocalOrInsideAnonymous(td) || isInPackageObject(td)
  }
}
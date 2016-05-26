package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

/**
 * @author ilyas
 */

import javax.swing.Icon

import com.intellij.lang.ASTNode
import com.intellij.lang.java.JavaLanguage
import com.intellij.navigation._
import com.intellij.openapi.editor.colors._
import com.intellij.openapi.util.Iconable
import com.intellij.psi._
import com.intellij.psi.impl._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import com.intellij.util.VisibilityIcons
import org.jetbrains.plugins.scala.conversion.JavaToScala
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScModifierList
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody, ScTemplateParents}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScToplevelElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers.SignatureNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScMemberOrLocal, ScTemplateDefinitionStub}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameterType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import _root_.scala.collection.mutable.ArrayBuffer
import scala.annotation.tailrec
import scala.collection.Seq
import scala.reflect.NameTransformer

abstract class ScTypeDefinitionImpl protected (stub: StubElement[ScTemplateDefinition], nodeType: IElementType, node: ASTNode)
extends ScalaStubBasedElementImpl(stub, nodeType, node) with ScTypeDefinition with PsiClassFake {
  override def hasTypeParameters: Boolean = typeParameters.nonEmpty

  override def add(element: PsiElement): PsiElement = {
    element match {
      case member: PsiMember if member.getLanguage.isKindOf(JavaLanguage.INSTANCE) =>
        val newMemberText = JavaToScala.convertPsiToText(member).trim()
        val mem: Option[ScMember] = member match {
          case method: PsiMethod =>
            Some(ScalaPsiElementFactory.createMethodFromText(newMemberText, getManager))
          case _ => None
        }
        mem match {
          case Some(m) => addMember(m, None)
          case _ => super.add(element)
        }
      case mem: ScMember => addMember(mem, None)
      case _ => super.add(element)
    }
  }

  override def getSuperTypes: Array[PsiClassType] = {
    superTypes.flatMap {
      case tp =>
        val psiType = tp.toPsiType(getProject, getResolveScope)
        psiType match {
          case c: PsiClassType => Seq(c)
          case _ => Seq.empty
        }
    }.toArray
  }

  override def isAnnotationType: Boolean = {
    val annotation = ScalaPsiManager.instance(getProject).getCachedClass("scala.annotation.Annotation",getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
    if (annotation == null) return false
    ScalaPsiManager.instance(getProject).cachedDeepIsInheritor(this, annotation)
  }

  def getType(ctx: TypingContext)  = {
    val parentClass: ScTemplateDefinition = containingClass
    if (typeParameters.isEmpty) {
      if (parentClass != null) {
        Success(ScProjectionType(ScThisType(parentClass), this, superReference = false), Some(this))
      } else {
        Success(ScalaType.designator(this), Some(this))
      }
    } else {
      if (parentClass != null) {
        Success(ScParameterizedType(ScProjectionType(ScThisType(parentClass), this, superReference = false),
          typeParameters.map(TypeParameterType(_))), Some(this))
      } else {
        Success(ScParameterizedType(ScalaType.designator(this),
          typeParameters.map(TypeParameterType(_))), Some(this))
      }
    }
  }

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false): TypeResult[ScType] = {
    def args = typeParameters.map(TypeParameterType(_))
    def innerType = if (typeParameters.isEmpty) ScalaType.designator(this)
    else ScParameterizedType(ScalaType.designator(this), args)
    val parentClazz = containingClass
    if (parentClazz != null) {
      val tpe: ScType = if (!thisProjections) parentClazz.getTypeWithProjections(TypingContext.empty, thisProjections = false).
        getOrElse(return Failure("Cannot resolve parent class", Some(this)))
      else ScThisType(parentClazz)

      val innerProjection = ScProjectionType(tpe, this, superReference = false)
      Success(if (typeParameters.isEmpty) innerProjection
              else ScParameterizedType(innerProjection, args), Some(this))
    } else Success(innerType, Some(this))
  }

  override def getModifierList: ScModifierList = super[ScTypeDefinition].getModifierList

  override def hasModifierProperty(name: String): Boolean = super[ScTypeDefinition].hasModifierProperty(name)

  override def getNavigationElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorClass
    case _ => this
  }

  private def hasSameScalaKind(other: PsiClass) = (this, other) match {
    case (_: ScTrait, _: ScTrait)
            | (_: ScObject, _: ScObject)
            | (_: ScClass, _: ScClass) => true
    case _ => false
  }

  def getSourceMirrorClass: PsiClass = {
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
      val parentSourceMirror = classParent.asInstanceOf[ScTypeDefinitionImpl].getSourceMirrorClass
      parentSourceMirror match {
        case td: ScTypeDefinitionImpl => for (i <- td.typeDefinitions if name == i.name && hasSameScalaKind(i))
          return i
        case _ => this
      }
    }
    this
  }

  override def isLocal: Boolean = {
    val stub: StubElement[_ <: PsiElement] = this match {
      case st: ScalaStubBasedElementImpl[_] => st.getStub
      case _ => null
    }
    stub match {
      case memberOrLocal: ScMemberOrLocal =>
        return memberOrLocal.isLocal
      case _ =>
    }
    containingClass == null && PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition]) != null
  }

  def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getContainingClass: PsiClass = {
    super[ScTypeDefinition].getContainingClass match {
      case o: ScObject => o.fakeCompanionClassOrCompanionClass
      case containingClass => containingClass
    }
  }

  override final def getQualifiedName: String = {
    val stub = getStub
    if (stub != null) stub.asInstanceOf[ScTemplateDefinitionStub].javaQualName
    else javaQualName()
  }

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  private def javaQualName(): String = {
    var res = qualifiedName(".", encodeName = true).split('.').map { s =>
      if (s.startsWith("`") && s.endsWith("`") && s.length > 2) s.drop(1).dropRight(1)
      else s
    }.mkString(".")
    this match {
      case o: ScObject =>
        if (o.isPackageObject) res = res + ".package$"
        else res = res + "$"
      case _ =>
    }
    res
  }

  override def qualifiedName: String = {
    val stub = getStub
    if (stub != null) stub.asInstanceOf[ScTemplateDefinitionStub].qualName
    else qualName()
  }

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  private def qualName(): String = qualifiedName(".")

  override def getExtendsListTypes: Array[PsiClassType] = innerExtendsListTypes

  override def getImplementsListTypes: Array[PsiClassType] = innerExtendsListTypes

  def getTruncedQualifiedName: String = qualifiedName(".", trunced = true)

  def getQualifiedNameForDebugger: String = {
    containingClass match {
      case td: ScTypeDefinition => td.getQualifiedNameForDebugger + "$" + transformName(encodeName = true, name)
      case _ =>
        if (this.isPackageObject) qualifiedName("", encodeName = true) + ".package"
        else qualifiedName("$", encodeName = true)
    }
  }

  protected def transformName(encodeName: Boolean, name: String): String = {
    if (!encodeName) name
    else {
      val deticked =
        if (name.startsWith("`") && name.endsWith("`") && name.length() > 1)
          name.substring(1, name.length() - 1)
        else name
      NameTransformer.encode(deticked)
    }
  }

  protected def qualifiedName(classSeparator: String, trunced: Boolean = false,
                              encodeName: Boolean = false): String = {
    // Returns prefix with convenient separator sep
    @tailrec
    def _packageName(e: PsiElement, sep: String, k: (String) => String): String = e.getContext match {
      case o: ScObject if o.isPackageObject && o.name == "`package`" => _packageName(o, sep, k)
      case _: ScClass | _: ScTrait if trunced => k("")
      case t: ScTypeDefinition => _packageName(t, sep, (s) => {
        val name = t.name
        k(s + transformName(encodeName, name) + sep)
      })
      case p: ScPackaging => _packageName(p, ".", (s) => k(s + p.getPackageName + "."))
      case f: ScalaFile => val pn = ""; k(if (pn.length > 0) pn + "." else "")
      case _: PsiFile | null => k("")
      case _: ScBlock => k("")
      case parent: ScTemplateBody => _packageName(parent, sep, k)
      case parent: ScExtendsBlock => _packageName(parent, sep, k)
      case parent: ScTemplateParents => _packageName(parent, sep, k)
      case parent => _packageName(parent, sep, identity)
    }

    val packageName = _packageName(this, classSeparator, identity)
    packageName + transformName(encodeName, name)
  }

  override def getPresentation: ItemPresentation = {
    val presentableName = this match {
      case o: ScObject if o.isPackageObject && o.name == "`package`" =>
        val packageName = o.qualifiedName.stripSuffix(".`package`")
        val index = packageName.lastIndexOf('.')
        if (index < 0) packageName else packageName.substring(index + 1, packageName.length)
      case _ => name
    }

    new ItemPresentation() {
      def getPresentableText: String = presentableName

      def getTextAttributesKey: TextAttributesKey = null

      def getLocationString: String = getPath match {
        case "" => "<default>"
        case p => '(' + p + ')'
      }

      override def getIcon(open: Boolean) = ScTypeDefinitionImpl.this.getIcon(0)
    }
  }


  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = {
    super[ScTypeDefinition].findMethodBySignature(patternMethod, checkBases)
  }

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = {
    super[ScTypeDefinition].findMethodsBySignature(patternMethod, checkBases)
  }

  import _root_.java.util.{Collection => JCollection, List => JList}

  import com.intellij.openapi.util.{Pair => IPair}

  override def findMethodsAndTheirSubstitutorsByName(name: String,
                                                     checkBases: Boolean): JList[IPair[PsiMethod, PsiSubstitutor]] = {
    super[ScTypeDefinition].findMethodsAndTheirSubstitutorsByName(name, checkBases)
  }

  override def getAllMethodsAndTheirSubstitutors: JList[IPair[PsiMethod, PsiSubstitutor]] = {
    super[ScTypeDefinition].getAllMethodsAndTheirSubstitutors
  }

  override def getVisibleSignatures: JCollection[HierarchicalMethodSignature] = {
    super[ScTypeDefinition].getVisibleSignatures
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    super[ScTypeDefinition].findMethodsByName(name, checkBases)
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    super[ScTypeDefinition].findFieldByName(name, checkBases)
  }

  override def checkDelete() {
  }

  override def delete() {
    var toDelete: PsiElement = this
    var parent: PsiElement = getParent
    while (parent.isInstanceOf[ScToplevelElement] && parent.asInstanceOf[ScToplevelElement].typeDefinitions.length == 1) {
      toDelete = parent
      parent = toDelete.getParent
    }
    toDelete match {
      case file: ScalaFile => file.delete()
      case _ => parent.getNode.removeChild(toDelete.getNode)
    }
  }

  override def getTypeParameters = typeParameters.toArray

  override def getSupers: Array[PsiClass] = {
    val direct = extendsBlock.supers.toArray
    val res = new ArrayBuffer[PsiClass]
    res ++= direct
    for (sup <- direct if !res.contains(sup)) res ++= sup.getSupers
    // return strict superclasses
    res.filter(_ != this).toArray
  }

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean =
    super[ScTypeDefinition].isInheritor(baseClass, deep)


  def signaturesByName(name: String): Seq[PhysicalSignature] = {
    (for ((s: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(this).forName(name)._1) yield s) ++
            syntheticMethodsNoOverride.filter(_.name == name).map(new PhysicalSignature(_, ScSubstitutor.empty))
  }

  override def getNameIdentifier: PsiIdentifier = {
    Predef.assert(nameId != null, "Class hase null nameId. Class text: " + getText) //diagnostic for EA-20122
    new JavaIdentifier(nameId)
  }

  override def getIcon(flags: Int): Icon = {
    val icon = getIconInner
    return icon //todo: remove, when performance issues will be fixed
    if (!this.isValid) return icon //to prevent Invalid access: EA: 13535
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !isWritable
    val rowIcon = ElementBase.createLayeredIcon(this, icon, ElementPresentationUtil.getFlags(this, isLocked))
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      val accessLevel = {
        if (hasModifierProperty("private")) PsiUtil.ACCESS_LEVEL_PRIVATE
        else if (hasModifierProperty("protected")) PsiUtil.ACCESS_LEVEL_PROTECTED
        else PsiUtil.ACCESS_LEVEL_PUBLIC
      }
      VisibilityIcons.setVisibilityIcon(accessLevel, rowIcon)
    }
    rowIcon
  }

  protected def getIconInner: Icon

  override def getDocComment: PsiDocComment = super[ScTypeDefinition].getDocComment


  override def isDeprecated: Boolean = {
    val stub = getStub
    if (stub != null) {
      return stub.asInstanceOf[ScTemplateDefinitionStub].isDeprecated
    }
    hasAnnotation("scala.deprecated").isDefined || hasAnnotation("java.lang.Deprecated").isDefined
  }

  override def getInnerClasses: Array[PsiClass] = {
    def ownInnerClasses = members.filter(_.isInstanceOf[PsiClass]).map(_.asInstanceOf[PsiClass]).toArray

    ScalaPsiUtil.getBaseCompanionModule(this) match {
      case Some(o: ScObject) =>
        val res: ArrayBuffer[PsiClass] = new ArrayBuffer[PsiClass]()
        val innerClasses = ownInnerClasses
        res ++= innerClasses
        o.members.foreach {
          case o: ScObject => o.fakeCompanionClass match {
            case Some(clazz) =>
              res += o
              res += clazz
            case None =>
              res += o
          }
          case t: ScTrait =>
            res += t
            res += t.fakeCompanionClass
          case c: ScClass => res += c
          case _ =>
        }
        res.toArray
      case _ => ownInnerClasses
    }
  }

  override def getAllInnerClasses: Array[PsiClass] = {
    PsiClassImplUtil.getAllInnerClasses(this)
  }

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    super[ScTypeDefinition].findInnerClassByName(name, checkBases)
  }

  override def getAllFields: Array[PsiField] = {
    super[ScTypeDefinition].getAllFields
  }

  override def getOriginalElement: PsiElement = {
    ScalaPsiImplementationHelper.getOriginalClass(this)
  }

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }

  override def getAllMethods: Array[PsiMethod] = getAllMethodsWithNames.map(_._1).toArray

  def getAllMethodsWithNames: Seq[(PsiMethod, String)] = {
    (getConstructors map {
      case constuctor => (constuctor, constuctor.getName)
    }) ++
      (TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) flatMap {
        case signatureNode => this.processPsiMethodsForNode(signatureNode,
          isStatic = false,
          isInterface = isInterfaceNode(signatureNode))
      }) ++
      (syntheticMethodsNoOverride map {
        new PhysicalSignature(_, ScSubstitutor.empty)
      } map {
        new SignatureNodes.Node(_, ScSubstitutor.empty)
      } flatMap {
        this.processPsiMethodsForNode(_, isStatic = false, isInterface = isInterface)
      })
  }

  protected def isInterfaceNode(node: SignatureNodes.Node): Boolean = node.info.namedElement match {
    case definition: ScTypedDefinition if definition.isAbstractMember => true
    case _ => false
  }
}
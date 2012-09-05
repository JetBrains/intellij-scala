package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

/**
 * @author ilyas
 */

import com.intellij.openapi.util.{Pair, Iconable}
import api.ScalaFile
import _root_.scala.collection.mutable.ArrayBuffer
import com.intellij.psi._
import com.intellij.openapi.editor.colors._
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import com.intellij.navigation._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.impl._
import com.intellij.util.VisibilityIcons
import javax.swing.Icon
import psi.stubs.ScTemplateDefinitionStub
import synthetic.JavaIdentifier
import types._
import fake.FakePsiMethod
import api.base.{ScPrimaryConstructor, ScModifierList}
import api.toplevel.{ScToplevelElement, ScTypedDefinition}
import result.{TypeResult, Failure, Success, TypingContext}
import util.{PsiUtil, PsiTreeUtil}
import collection.Seq
import api.statements.ScAnnotationsHolder
import api.expr.ScBlock
import api.toplevel.templates.{ScTemplateParents, ScExtendsBlock, ScTemplateBody}
import reflect.NameTransformer
import extensions.toPsiNamedElementExt

abstract class ScTypeDefinitionImpl extends ScalaStubBasedElementImpl[ScTemplateDefinition] with ScTypeDefinition with PsiClassFake {
  override def hasTypeParameters: Boolean = typeParameters.length > 0

  override def add(element: PsiElement): PsiElement = {
    element match {
      case mem: ScMember => addMember(mem, None)
      case _ => super.add(element)
    }
  }

  override def getSuperTypes: Array[PsiClassType] = {
    superTypes.flatMap {
      case tp =>
        val psiType = ScType.toPsi(tp, getProject, getResolveScope)
        psiType match {
          case c: PsiClassType => Seq(c)
          case _ => Seq.empty
        }
    }.toArray
  }

  override def isAnnotationType: Boolean = {
    val annotation = ScalaPsiManager.instance(getProject).getCachedClass("scala.annotation.Annotation",getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
    if (annotation == null) return false
    isInheritor(annotation, deep = true)
  }

  def getType(ctx: TypingContext)  = {
    if (typeParameters.length == 0)
      Success(ScType.designator(this), Some(this))
    else {
      Success(ScParameterizedType(ScType.designator(this),
        typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty))), Some(this))
    }
  }

  def getTypeWithProjections(ctx: TypingContext, thisProjections: Boolean = false): TypeResult[ScType] = {
    def args: Seq[ScTypeParameterType] = typeParameters.map(new ScTypeParameterType(_, ScSubstitutor.empty))
    def innerType = if (typeParameters.length == 0) ScType.designator(this)
                    else ScParameterizedType(ScType.designator(this), args)
    val parentClazz = ScalaPsiUtil.getPlaceTd(this)
    if (parentClazz != null) {
      val tpe: ScType = if (!thisProjections) parentClazz.getTypeWithProjections(TypingContext.empty, false).getOrElse(return Failure("Cannot resolve parent class", Some(this)))
      else ScThisType(parentClazz)

      val innerProjection: ScProjectionType = ScProjectionType(tpe, this, ScSubstitutor.empty, false)
      Success(if (typeParameters.length == 0) innerProjection
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

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getContainingClass: PsiClass = {
    super[ScTypeDefinition].getContainingClass match {
      case o: ScObject => o.fakeCompanionClassOrCompanionClass
      case containingClass => containingClass
    }
  }

  @volatile
  private var qualName: String = null
  @volatile
  private var qualNameModCount: Long = -1

  @volatile
  private var javaQualName: String = null
  @volatile
  private var javaQualNameModCount: Long = -1

  override final def getQualifiedName: String = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTemplateDefinitionStub].javaQualName
    } else {
      val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
      if (javaQualName != null && count == javaQualNameModCount) return javaQualName
      var res = qualifiedName(".", encodeName = true)
      this match {
        case o: ScObject =>
          if (o.isPackageObject) res = res + ".package$"
          else res = res + "$"
        case _ =>
      }
      javaQualNameModCount = count
      javaQualName = res
      res
    }
  }

  override def qualifiedName: String = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTemplateDefinitionStub].qualName
    } else {
      val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
      if (qualName != null && count == qualNameModCount) return qualName
      val res = qualifiedName(".")
      qualNameModCount = count
      qualName = res
      res
    }
  }

  override def getExtendsListTypes: Array[PsiClassType] = innerExtendsListTypes

  override def getImplementsListTypes: Array[PsiClassType] = innerExtendsListTypes

  def getTruncedQualifiedName: String = qualifiedName(".", true)

  def getQualifiedNameForDebugger: String = qualifiedName("$", encodeName = true)

  private def transformName(encodeName: Boolean, name: String): String = {
    if (!encodeName) name
    else {
      val deticked =
        if (name.startsWith("`") && name.endsWith("`") && name.length() > 1)
          name.substring(1, name.length() - 1)
        else name
      NameTransformer.encode(deticked)
    }
  }

  private def qualifiedName(classSeparator: String, trunced: Boolean = false,
                            encodeName: Boolean = false): String = {
    // Returns prefix with convenient separator sep
    def _packageName(e: PsiElement, sep: String, k: (String) => String): String = e.getContext match {
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
      case parent => _packageName(parent, sep, identity _)
    }

    val packageName = _packageName(this, classSeparator, identity _)
    packageName + transformName(encodeName, name)
  }

  override def getPresentation: ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText: String = name

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

  import com.intellij.openapi.util.{Pair => IPair}
  import _root_.java.util.{List => JList}
  import _root_.java.util.{Collection => JCollection}

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
    (for ((s: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(this).forName(name)._1) yield s).toSeq ++
            syntheticMembers.filter(_.name == name).map(new PhysicalSignature(_, ScSubstitutor.empty))
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
    hasAnnotation("scala.deprecated") != None || hasAnnotation("java.lang.Deprecated") != None
  }

  override def getInnerClasses: Array[PsiClass] = {
    members.filter(_.isInstanceOf[PsiClass]).map(_.asInstanceOf[PsiClass]).toArray
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
}
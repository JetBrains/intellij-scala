package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

/**
 * @author ilyas
 */

import _root_.java.lang.String
import _root_.java.util.List
import com.intellij.openapi.util.{Pair, Iconable}
import api.ScalaFile
import com.intellij.psi.search.GlobalSearchScope
import _root_.scala.collection.immutable.Set
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
import Misc._
import types._
import fake.FakePsiMethod
import api.base.{ScPrimaryConstructor, ScModifierList}
import api.toplevel.{ScToplevelElement, ScTypedDefinition}
import com.intellij.openapi.project.DumbService
import nonvalue.Parameter
import result.{TypeResult, Failure, Success, TypingContext}
import util.{PsiUtil, PsiTreeUtil}
import collection.Seq
import api.statements.{ScVariable, ScValue, ScAnnotationsHolder}
import api.statements.params.ScClassParameter
import com.intellij.openapi.util.text.StringUtil
import api.expr.ScBlock
import api.toplevel.templates.{ScTemplateParents, ScExtendsBlock, ScTemplateBody}
import reflect.NameTransformer

abstract class ScTypeDefinitionImpl extends ScalaStubBasedElementImpl[ScTemplateDefinition] with ScTypeDefinition with PsiClassFake {
  override def hasTypeParameters: Boolean = typeParameters.length > 0

  override def add(element: PsiElement): PsiElement = {
    element match {
      case mem: ScMember => addMember(mem, None)
      case _ => super.add(element)
    }
  }

  override def isAnnotationType: Boolean = {
    val annotation = JavaPsiFacade.getInstance(getProject).findClass("scala.Annotation",getResolveScope)
    if (annotation == null) return false
    isInheritor(annotation, true)
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

      val innerProjection: ScProjectionType = ScProjectionType(tpe, this, ScSubstitutor.empty)
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
    val name = getName
    if (classParent == null) {
      val classes: Array[PsiClass] = getContainingFile.getNavigationElement.asInstanceOf[PsiClassOwner].getClasses
      val classesIterator = classes.iterator
      while (classesIterator.hasNext) {
        val c = classesIterator.next()
        if (name == c.getName && hasSameScalaKind(c)) return c
      }
    } else {
      val parentSourceMirror = classParent.asInstanceOf[ScTypeDefinitionImpl].getSourceMirrorClass
      parentSourceMirror match {
        case td: ScTypeDefinitionImpl => for (i <- td.typeDefinitions if name == i.getName && hasSameScalaKind(i))
          return i
        case _ => this
      }
    }
    this
  }

  def nameId = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getContainingClass = super[ScTypeDefinition].getContainingClass

  @volatile
  private var qualName: String = null
  @volatile
  private var qualNameModCount: Long = -1

  override def getQualifiedName: String = {
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
      case f: ScalaFile => val pn = f.getPackageName; k(if (pn.length > 0) pn + "." else "")
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

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    val filterFun = (m: PsiMethod) => m.getName == name
    val arrayOfMethods: Array[PsiMethod] = if (checkBases) getAllMethods else functions.toArray[PsiMethod]
    (arrayOfMethods ++ syntheticMembers).filter(filterFun)
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

  /**
   * Do not use it for scala. Use functions method instead.
   */
  override def getMethods: Array[PsiMethod] = {
    val buffer: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
    def methods(td: ScTemplateDefinition): Seq[PsiMethod] = {
      td.members.flatMap {
        p => {
          import api.statements.{ScVariable, ScFunction, ScValue}
          p match {
            case primary: ScPrimaryConstructor => Array[PsiMethod](primary)
            case function: ScFunction => Array[PsiMethod](function)
            case value: ScValue => {
              for (binding <- value.declaredElements) yield new FakePsiMethod(binding, value.hasModifierProperty _)
            }
            case variable: ScVariable => {
              for (binding <- variable.declaredElements) yield new FakePsiMethod(binding, variable.hasModifierProperty _)
            }
            case _ => Array[PsiMethod]()
          }
        }
      }
    }
    buffer ++= methods(this)
    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(td: ScTemplateDefinition) => buffer ++= methods(td) //to see from Java methods from companion modules.
      case _ =>
    }
    buffer.toArray
  }

  override def getAllMethods: Array[PsiMethod] = {
    val buffer: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
    val methodsIterator = TypeDefinitionMembers.getSignatures(this).iterator
    while (methodsIterator.hasNext) {
      methodsIterator.next()._1 match {
        case sig: PhysicalSignature => buffer += sig.method
        case s: Signature =>
          s.namedElement match {
            case Some(t: ScTypedDefinition) =>
              val context = ScalaPsiUtil.nameContext(t)
              buffer += new FakePsiMethod(t, context match {
                case o: PsiModifierListOwner => o.hasModifierProperty _
                case _ => (s: String) => false
              })

              context match {
                case annotated: ScAnnotationsHolder => {
                  BeanProperty.processBeanPropertyDeclarationsInternal(annotated, context, t) { element =>
                    buffer += element
                    true
                  }
                }
                case _ =>
              }
            case _ =>
          }
        case _ =>
      }
    }
    buffer ++= syntheticMembers

    //todo: methods from companion module?
    buffer.toArray
  }

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean =
    super[ScTypeDefinition].isInheritor(baseClass, deep)


  def signaturesByName(name: String): Seq[PhysicalSignature] = {
    (for ((s: PhysicalSignature, _) <- TypeDefinitionMembers.getSignatures(this) if s.name == name) yield s).toSeq ++
            syntheticMembers.filter(_.getName == name).map(new PhysicalSignature(_, ScSubstitutor.empty))
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
    val rowIcon = ElementBase.createLayeredIcon(icon, ElementPresentationUtil.getFlags(this, isLocked))
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      val accessLevel = {
        if (hasModifierProperty("private")) PsiUtil.ACCESS_LEVEL_PRIVATE
        else if (hasModifierProperty("protected")) PsiUtil.ACCESS_LEVEL_PROTECTED
        else PsiUtil.ACCESS_LEVEL_PUBLIC
      }
      VisibilityIcons.setVisibilityIcon(accessLevel, rowIcon);
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

  //Java sources uses this method. Really it's not very useful. Parameter checkBases ignored
  override def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): List[Pair[PsiMethod, PsiSubstitutor]] = {
    super[ScTypeDefinition].findMethodsAndTheirSubstitutorsByName(name, checkBases)
  }

  override def getInnerClasses: Array[PsiClass] = {
    members.filter(_.isInstanceOf[PsiClass]).map(_.asInstanceOf[PsiClass]).toArray
  }

  override def getAllInnerClasses: Array[PsiClass] = {
    members.filter(_.isInstanceOf[PsiClass]).map(_.asInstanceOf[PsiClass]).toArray  //todo: possible add base classes inners
  }

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    (if (checkBases) {
      //todo: possibly add base classes inners
      members.find(p => p.isInstanceOf[PsiClass] && p.asInstanceOf[PsiClass].getName == name).getOrElse(null)
    } else {
      members.find(p => p.isInstanceOf[PsiClass] && p.asInstanceOf[PsiClass].getName == name).getOrElse(null)
    }).asInstanceOf[PsiClass]
  }
}
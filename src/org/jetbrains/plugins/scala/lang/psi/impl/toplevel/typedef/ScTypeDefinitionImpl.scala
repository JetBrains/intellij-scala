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
import _root_.java.util.{List, ArrayList}
import com.intellij.openapi.util.{Pair, Iconable}
import api.ScalaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.RenameUtil
import _root_.scala.collection.immutable.Set
import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.psi._
import com.intellij.openapi.editor.colors._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.navigation._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.util.IncorrectOperationException
import com.intellij.psi.impl._
import com.intellij.util.VisibilityIcons
import javax.swing.Icon
import psi.stubs.ScTypeDefinitionStub
import stubs.StubElement
import synthetic.JavaIdentifier
import Misc._
import util.{PsiUtil, PsiTreeUtil}
import source.PsiFileImpl
import types._
import fake.FakePsiMethod
import api.base.patterns.ScBindingPattern
import api.base.{ScPrimaryConstructor, ScModifierList}
import api.toplevel.{ScToplevelElement, ScTypedDefinition}
import com.intellij.openapi.project.DumbService
import result.{Success, TypingContext}

abstract class ScTypeDefinitionImpl extends ScalaStubBasedElementImpl[ScTypeDefinition] with ScTypeDefinition with PsiClassFake {
  override def add(element: PsiElement): PsiElement = {
    element match {
      case mem: ScMember => addMember(mem, None)
      case _ => super.add(element)
    }
  }

  def getType(ctx: TypingContext)  = Success(new ScDesignatorType(this), Some(this))

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

  private def getSourceMirrorClass: PsiClass = {
    val classParent = PsiTreeUtil.getParentOfType(this, classOf[ScTypeDefinition], true)
    val name = getName
    if (classParent == null) {
      for (c <- getContainingFile.getNavigationElement.asInstanceOf[PsiClassOwner].getClasses) {
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

  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getTextOffset: Int = nameId.getTextRange.getStartOffset

  override def getContainingClass = super[ScTypeDefinition].getContainingClass

  override def getQualifiedName: String = qualifiedName(".")

  def getQualifiedNameForDebugger: String = qualifiedName("$")

  private def qualifiedName(classSeparator: String): String = {

    // Returns prefix with convenient separator sep
    def _packageName(e: PsiElement, sep: String, k: (String) => String): String = e.getParent match {
      case t: ScTypeDefinition => _packageName(t, sep, (s) => k(s + t.name + sep))
      case p: ScPackaging => _packageName(p, ".", (s) => k(s + p.getPackageName + "."))
      case f: ScalaFile => val pn = f.getPackageName; k(if (pn.length > 0) pn + "." else "")
      case _: PsiFile | null => k("")
      case parent => _packageName(parent, sep, identity _)
    }

    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTypeDefinitionStub].qualName
    } else {
      val packageName = _packageName(this, classSeparator, identity _)
      packageName + name
    }
  }

  override def getPresentation(): ItemPresentation = {
    new ItemPresentation() {
      def getPresentableText(): String = name

      def getTextAttributesKey(): TextAttributesKey = null

      def getLocationString(): String = getPath match {
        case "" => "<default>"
        case p => '(' + p + ')'
      }

      override def getIcon(open: Boolean) = ScTypeDefinitionImpl.this.getIcon(0)
    }
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    val filterFun = (m: PsiMethod) => m.getName == name
    (if (checkBases) getAllMethods else functions.toArray[PsiMethod]).filter(filterFun)
  }

  override def checkDelete() {
  }

  override def delete() = {
    var toDelete: PsiElement = this
    var parent: PsiElement = getParent
    while (parent.isInstanceOf[ScToplevelElement] && parent.asInstanceOf[ScToplevelElement].typeDefinitions.length == 1) {
      toDelete = parent
      parent = toDelete.getParent
    }
    toDelete match {
      case file: ScalaFile => file.delete
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

  private[typedef] var lockCompanionRename = false
  override def setName(name: String): PsiElement = setName(name, true)
  private[typedef] def setName(name: String, renameCompanion: Boolean): PsiElement = {
    val id = nameId.getNode
    val parent = id.getTreeParent
    def shortName(s: String): String = {
      if (!s.endsWith(".scala")) return null
      else return s.substring(0, s.length - 6)
    }
    if (id.getText == shortName(id.getPsi.getContainingFile.getName)) {
      this.getContainingFile match {
        case x: ScalaFile => x.setName(name + ".scala")
        case _ =>
      }
    }

    lockCompanionRename = true
    if (renameCompanion) ScalaPsiUtil.getCompanionModule(this) match {
      case Some(td: ScTypeDefinitionImpl) if !td.lockCompanionRename => RenameUtil.doRename(td, name,
        RenameUtil.findUsages(td, name, false, false, new _root_.java.util.HashMap[PsiElement, String]()), getProject,
        new RefactoringElementListener {
          def elementMoved(newElement: PsiElement): Unit = {}

          def elementRenamed(newElement: PsiElement): Unit = {}
        })
      case _ =>
    }
    lockCompanionRename = false

    super.setName(name)
  }

  /**
   * Do not use it for scala. Use functions method instead.
   */
  override def getMethods: Array[PsiMethod] = {
    val buffer: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
    buffer ++= members.flatMap {
      p => {
        import api.statements.{ScVariable, ScFunction, ScValue}
        import synthetic.PsiMethodFake
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
    return buffer.toArray
  }

  override def getAllMethods: Array[PsiMethod] = {
    val buffer: ArrayBuffer[PsiMethod] = new ArrayBuffer[PsiMethod]
    buffer ++= TypeDefinitionMembers.getMethods(this).toArray.map[PsiMethod, Array[PsiMethod]](_._1.method)
    for ((t, _) <- TypeDefinitionMembers.getVals(this).toArray) {
       t match {
         case t: ScTypedDefinition => {
           val context = ScalaPsiUtil.nameContext(t)
           buffer += new FakePsiMethod(t, context match {
              case o: PsiModifierListOwner => o.hasModifierProperty _
              case _ => (s: String) => false
            })
         }
         case _ =>
       }
    }
    return buffer.toArray
  }

  import com.intellij.psi.scope.PsiScopeProcessor

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean = {
    def isInheritorInner(base: PsiClass, drv: PsiClass, deep: Boolean, visited: Set[PsiClass]): Boolean = {
      if (visited.contains(drv)) false
      else drv match {
        case drg: ScTypeDefinition => drg.superTypes.find{
          t => ScType.extractClassType(t) match {
            case Some((c, _)) => {
              val value = baseClass match { //todo: it was wrong to write baseClass.isInstanceOf[c.type]
                case _: ScTrait if c.isInstanceOf[ScTrait] => true
                case _: ScClass if c.isInstanceOf[ScClass] => true
                case _ if !c.isInstanceOf[ScTypeDefinition] => true
                case _ => false
              }
              (c.getQualifiedName == baseClass.getQualifiedName && value) || (deep && isInheritorInner(base, c, deep, visited + drg))
            }
            case _ => false
          }
        }
        case _ => drv.getSuperTypes.find{
          psiT =>
                  val c = psiT.resolveGenerics.getElement
                  if (c == null) false else c == baseClass || (deep && isInheritorInner(base, c, deep, visited + drv))
        }
      }
    }
    if (DumbService.getInstance(baseClass.getProject).isDumb) return false //to prevent failing during indecies
    isInheritorInner(baseClass, this, deep, Set.empty)
  }

  def functionsByName(name: String) =
    for ((_, n) <- TypeDefinitionMembers.getMethods(this) if n.info.method.getName == name) yield n.info.method


  override def getNameIdentifier: PsiIdentifier = new JavaIdentifier(nameId)

  override def getIcon(flags: Int) = {
    val icon = getIconInner
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

  override def getImplementsListTypes = getExtendsListTypes

  override def getExtendsListTypes = {
    val eb = extendsBlock
    if (eb != null) {
      val tp = eb.templateParents
      tp match {
        case Some(tp1) => (for (te <- tp1.typeElements;
                                t = te.cachedType.getOrElse(Any);
                                asPsi = ScType.toPsi(t, getProject, GlobalSearchScope.allScope(getProject));
                                if asPsi.isInstanceOf[PsiClassType]) yield asPsi.asInstanceOf[PsiClassType]).toArray[PsiClassType]
        case _ => PsiClassType.EMPTY_ARRAY
      }
    } else PsiClassType.EMPTY_ARRAY
  }


  override def getDocComment: PsiDocComment = super[ScTypeDefinition].getDocComment


  override def isDeprecated: Boolean = super[ScTypeDefinition].isDeprecated

  //Java sources uses this method. Really it's not very useful. Parameter checkBases ignored
  override def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): List[Pair[PsiMethod, PsiSubstitutor]] = {
    val functions = functionsByName(name).filter(_.getContainingClass == this)
    val res = new ArrayList[Pair[PsiMethod, PsiSubstitutor]]()
    for {(_, n) <- TypeDefinitionMembers.getMethods(this)
         substitutor = n.info.substitutor
         method = n.info.method
         if method.getName == name &&
                 method.getContainingClass == this
    } {
      res.add(new Pair[PsiMethod, PsiSubstitutor](method, ScalaPsiUtil.getPsiSubstitutor(substitutor, getProject)))
    }
    res
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
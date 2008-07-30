package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

/**
 * @author ilyas
 */

import api.base.{ScStableCodeReferenceElement, ScPrimaryConstructor}
import base.ScStableCodeReferenceElementImpl
import api.base.ScStableCodeReferenceElement
import api.base.types.ScTypeElement
import _root_.scala.collection.mutable.ArrayBuffer
import _root_.scala.collection.mutable.HashSet
import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.colors._
import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParamClause, ScTypeParam}
import psi.api.toplevel.packaging._
import psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.icons.Icons
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.navigation._
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.util.IncorrectOperationException
import com.intellij.util.IconUtil
import com.intellij.psi.impl._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.VisibilityIcons
import com.intellij.openapi.util.Iconable
import javax.swing.Icon
import api.statements.{ScFunction, ScTypeAlias}
import types.{ScSubstitutor, ScType}
import api.statements.{ScValue, ScVariable}

abstract class ScTypeDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeDefinition with PsiClassFake {
  def nameId() = findChildByType(ScalaTokenTypes.tIDENTIFIER)

  override def getQualifiedName: String = {
    def _packageName(e: PsiElement): String = e.getParent match {
      case t: ScTypeDefinition => _packageName(t) + "." + t.name
      case p: ScPackaging => _packageName(p) + "." + p.getPackageName
      case f: ScalaFile => f.getPackageName
      case null => ""
      case parent => _packageName(parent)
    }
    val packageName = _packageName(this)
    if (packageName.length > 0) packageName + "." + name else name
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

  protected def getIconInner: Icon

  override def getIcon(flags: Int): Icon = {
    if (!isValid) return null
    val icon = getIconInner
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !isWritable()
    val rowIcon = ElementBase.createLayeredIcon(icon, ElementPresentationUtil.getFlags(this, isLocked))
    if ((flags & Iconable.ICON_FLAG_VISIBILITY) != 0) {
      VisibilityIcons.setVisibilityIcon(getModifierList, rowIcon);
    }
    rowIcon
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = functions.filter((m: PsiMethod) =>
          m.getName == name // todo check base classes
    ).toArray

  def extendsBlock: ScExtendsBlock = findChildByClass(classOf[ScExtendsBlock])

  override def checkDelete() {
  }

  def members(): Seq[ScMember] =
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.members
    }) ++
            (extendsBlock.earlyDefinitions match {
              case None => Seq.empty
              case Some(earlyDefs) => earlyDefs.members
            }) ++ (findChild(classOf[ScPrimaryConstructor]) match {
      case None => Seq.empty
      case Some(x) => Array[ScMember](x)
    })

  def functions(): Seq[ScFunction] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.functions
    }

  def aliases(): Seq[ScTypeAlias] =
    extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.aliases
    }

  def allAliases: Seq[ScTypeAlias] = {
    val buf = new ArrayBuffer[ScTypeAlias]
    buf ++= aliases
    for (clazz <- getSupers if clazz.isInstanceOf[ScTypeDefinition]) buf ++= clazz.asInstanceOf[ScTypeDefinition].aliases
    return buf.toArray
  }

  def allVals: Seq[ScValue] = {
    allMembers.filter(_.isInstanceOf[ScValue]).map(_.asInstanceOf[ScValue])
  }

  def allVars: Seq[ScVariable] = {
    allMembers.filter(_.isInstanceOf[ScVariable]).map(_.asInstanceOf[ScVariable])
  }

  def allFields: Seq[PsiField] = {
    allMembers.filter(_.isInstanceOf[PsiField]).map(_.asInstanceOf[PsiField])
  }

  def allMembers: Seq[PsiMember] = {
    val buf = new ArrayBuffer[PsiMember]
    buf ++= members
    for (clazz <- getSupers) {
      if (clazz.isInstanceOf[ScTypeDefinition])
        buf ++= clazz.asInstanceOf[ScTypeDefinition].members
      else
        buf ++= clazz.getAllFields
    }
    return buf.toArray
  }

  def innerTypeDefinitions: Seq[ScTypeDefinition] =
    (extendsBlock.templateBody match {
      case None => Seq.empty
      case Some(body) => body.typeDefinitions
    })

  override def delete() = {
    var parent = getParent
    var remove: PsiElement = this
    while (parent.isInstanceOf[ScPackaging]) {
      remove = parent
      parent = parent.getParent
    }
    parent match {
      case f: ScalaFile => {
        if (f.getTypeDefinitions.length == 1) {
          f.delete
        } else {
          f.getNode.removeChild(remove.getNode)
        }
      }
      case e: ScalaPsiElement => e.getNode.removeChild(remove.getNode)
      case _ => throw new IncorrectOperationException("Invalid type definition")
    }
  }

  override def getTypeParameters = typeParameters.toArray

  override def getSupers: Array[PsiClass] = {
    val buf = new ArrayBuffer[PsiClass]
    val typeElements = new ArrayBuffer[ScTypeElement]
    extendsBlock.templateParents match {
      case None =>
      case Some(parents) => {
        parents match {
          case classParents: ScClassParents =>
            classParents.constructor match {
              case None => ()
              case Some(c) => typeElements += c.typeElement
            }
          case _ =>
        }
        typeElements ++= parents.typeElements.toArray
      }
    }
    for (element <- typeElements) {
      element.getFirstChild match {
        case x: ScStableCodeReferenceElement => {
          x.resolve match {
            case null =>
            case psiElement => {
              psiElement match {
                case x: PsiClass => buf += x
                case _ =>
              }
            }
          }
        }
        case _ =>
      }
    }
    return buf.toArray
  }

  override def getMethods = functions.toArray

  override def getAllMethods: Array[PsiMethod] = {
    val buffer = new ArrayBuffer[PsiMethod]
    getAllMethodsForClass(this, buffer, new HashSet[PsiClass])
    return buffer.toArray
  }
  private def getAllMethodsForClass(clazz: PsiClass, methods: ArrayBuffer[PsiMethod], visited: HashSet[PsiClass]) {
    if (visited.contains(clazz)) return
    visited += clazz
    methods ++= clazz.getMethods
    //todo: value definition is method?
    for (sup <- clazz.getSupers) getAllMethodsForClass(sup, methods, visited)
  }

  def superTypes() = extendsBlock.superTypes

  import com.intellij.psi.scope.{PsiScopeProcessor, ElementClassHint}

  import TypeDefinitionMembers.{ValueNodes, TypeNodes}

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (!processor.execute(this, state)) return false
    val substK = state.get(ScSubstitutor.key)
    val subst = if (substK == null) ScSubstitutor.empty else substK
    extendsBlock.templateParents match {
      case Some(p) if (PsiTreeUtil.isAncestor(p, place, true)) => {
        extendsBlock.earlyDefinitions match {
          case Some(ed) => for (m <- ed.members) {
            m match {
              case _var: ScVariable => for (declared <- _var.declaredElements) {
                if (!processor.execute(declared, state)) return false
              }
              case _val: ScValue => for (declared <- _val.declaredElements) {
                if (!processor.execute(declared, state)) return false
              }
            }
          }
          case None =>
        }
        true
      }
      case _ =>
        extendsBlock.earlyDefinitions match {
          case Some(ed) if PsiTreeUtil.isAncestor(ed, place, true) =>
          case _ =>
            if (shouldProcessVals(processor)) {
              for ((_, n) <- TypeDefinitionMembers.getVals(this)) {
                if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
              }
            }
            if (shouldProcessMethods(processor)) {
              for ((_, n) <- TypeDefinitionMembers.getMethods(this)) {
                if (!processor.execute(n.info.method, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
              }
            }
            if (shouldProcessTypes(processor)) {
              for ((_, n) <- TypeDefinitionMembers.getTypes(this)) {
                if (!processor.execute(n.info, state.put(ScSubstitutor.key, n.substitutor followed subst))) return false
              }
            }
        }

        true
    }
  }

  import scala.lang.resolve._, scala.lang.resolve.ResolveTargets._

  def shouldProcessVals(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => (kinds contains VAR) || (kinds contains VAL) || (kinds contains OBJECT)
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiVariable])
    }
  }

  def shouldProcessMethods(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains METHOD
    case _ => {
      val hint = processor.getHint(classOf[ElementClassHint])
      hint == null || hint.shouldProcess(classOf[PsiMethod])
    }
  }

  def shouldProcessTypes(processor: PsiScopeProcessor) = processor match {
    case BaseProcessor(kinds) => kinds contains CLASS
    case _ => false //important: do not process inner classes!
  }

  override def getContainingClass: PsiClass = getParent match {
    case eb: ScExtendsBlock => eb.getParent.asInstanceOf[ScTypeDefinition]
    case _ => null
  }

  override def isInheritor(clazz : PsiClass, deep : Boolean) = !superTypes.find {t =>
    ScType.extractClassType(t) match {
      case Some((c, _)) => c == clazz || (deep && c.isInheritor(clazz, deep))
      case _ => false
    }
  }.isEmpty
}
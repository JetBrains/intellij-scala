package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import scala.annotation.tailrec

import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.search.{LocalSearchScope, PackageScope, SearchScope}
import com.intellij.psi.util._
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, StubBasedExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScMemberOrLocal
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}
import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  * Date: 04.05.2008
  */
trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {
  def getContainingClass: PsiClass = containingClass

  def isPrivate: Boolean = hasModifierPropertyScala("private")

  def isProtected: Boolean = hasModifierPropertyScala("protected")

  def isPublic: Boolean = !isPrivate && !isProtected

  def isInstance: Boolean = !isLocal

  protected def synthNavElement: Option[PsiElement] = Option(getUserData(synthNavElemKey))
  def syntheticCaseClass: Option[ScClass] = Option(getUserData(synthCaseClassKey))
  def syntheticContainingClass: Option[ScTypeDefinition] = Option(getUserData(synthContainingClassKey))

  def setSynthetic(navElement: PsiElement): Unit = putUserData(synthNavElemKey, navElement)
  def setSyntheticCaseClass(cl: ScClass): Unit = putUserData(synthCaseClassKey, cl)
  def setSyntheticContainingClass(td: ScTypeDefinition): Unit = putUserData(synthContainingClassKey, td)

  def isSynthetic: Boolean = synthNavElement.nonEmpty
  def getSyntheticNavigationElement: Option[PsiElement] = synthNavElement


  /**
    * getContainingClassStrict(bar) == null in
    *
    * `object a { def foo { def bar = 0 }}`
    */
  @Cached(ModCount.anyScalaPsiModificationCount, this)
  def containingClass: ScTemplateDefinition = {
    if (isLocalByStub) null
    else containingClassInner
  }

  private def isLocalByStub = this.greenStub.exists {
    case m: ScMemberOrLocal => m.isLocal
    case _ => false
  }

  private def containingClassInner: ScTemplateDefinition = {
    def isCorrectContext(found: ScTemplateDefinition): Boolean = {
      val context = getContext
      context == found.extendsBlock ||
        found.extendsBlock.templateBody.contains(context) ||
        found.extendsBlock.earlyDefinitions.contains(context) ||
        found.physicalExtendsBlock.templateBody.contains(context) // in case a member is not present in the desugared extends block (e.g. deleted by a macro)
    }
    (getContainingClassLoose, this) match {
      case (null, _) => null
      case (_, fun: ScFunction) if fun.syntheticContainingClass.isDefined => fun.syntheticContainingClass.get
      case (found, fun: ScFunction) if fun.isSynthetic => found
      case (_, ta: ScTypeAlias) if ta.syntheticContainingClass.isDefined => ta.syntheticContainingClass.get
      case (_, td: ScTypeDefinition) if td.syntheticContainingClass.isDefined => td.syntheticContainingClass.get
      case (_, valVar: ScValueOrVariable) if valVar.syntheticContainingClass.isDefined => valVar.syntheticContainingClass.get
      case (found, td: ScTypeDefinition) if td.isSynthetic => found
      case (found, _: ScClassParameter | _: ScPrimaryConstructor) => found
      case (found, _) if isCorrectContext(found) => found
      case (_, _) => null // See SCL-3178
    }
  }

  def getContainingClassLoose: ScTemplateDefinition = {
    this.greenStub match {
      case Some(stub) => stub.getParentStubOfType(classOf[ScTemplateDefinition])
      case None =>
        child match {
          // TODO is all of this mess still necessary?!
          case c: ScClass if c.isCase =>
            this match {
              case fun: ScFunction if fun.isSyntheticApply || fun.isSyntheticUnapply ||
                fun.isSyntheticUnapplySeq =>
                //this is special case for synthetic apply and unapply methods
                ScalaPsiUtil.getCompanionModule(c) match {
                  case Some(td) => return td
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        PsiTreeUtil.getContextOfType(this, true, classOf[ScTemplateDefinition])
    }
  }

  def isLocal: Boolean = isLocalByStub || containingClassInner == null

  override def hasModifierProperty(name: String): Boolean = {
    name match {
      case PsiModifier.PUBLIC =>
        !hasModifierProperty("private") && !hasModifierProperty("protected")
      case PsiModifier.STATIC => containingClass.isInstanceOf[ScObject]
      case PsiModifier.PRIVATE =>
        getModifierList.accessModifier.exists(_.access == ScAccessModifier.Type.THIS_PRIVATE)
      case PsiModifier.PROTECTED =>
        getModifierList.accessModifier.exists(_.access == ScAccessModifier.Type.THIS_PROTECTED)
      case _ => super.hasModifierProperty(name)
    }
  }

  abstract override def getContainingFile: PsiFile = {
    syntheticContainingClass.map(_.getContainingFile).getOrElse(super.getContainingFile)
  }

  protected def isSimilarMemberForNavigation(m: ScMember, isStrict: Boolean) = false

  override def getNavigationElement: PsiElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled => getSourceMirrorMember
    case _ => this
  }

  private def getSourceMirrorMember: ScMember = getParent match {
    case tdb: ScTemplateBody => tdb.getParent match {
      case eb: ScExtendsBlock => eb.getParent match {
        case td: ScTypeDefinition => td.getNavigationElement match {
          case c: ScTypeDefinition =>
            val membersIterator = c.members.iterator
            val buf: ArrayBuffer[ScMember] = new ArrayBuffer[ScMember]
            while (membersIterator.hasNext) {
              val member = membersIterator.next()
              if (isSimilarMemberForNavigation(member, isStrict = false)) buf += member
            }
            if (buf.isEmpty) this
            else if (buf.length == 1) buf(0)
            else {
              val filter = buf.filter(isSimilarMemberForNavigation(_, isStrict = true))
              if (filter.isEmpty) buf(0)
              else filter(0)
            }
          case _ => this
        }
        case _ => this
      }
      case _ => this
    }
    case c: ScTypeDefinition if this.isInstanceOf[ScPrimaryConstructor] => //primary constructor
      c.getNavigationElement match {
        case td: ScClass =>
          td.constructor match {
            case Some(constr) => constr
            case _ => this
          }
        case _ => this
      }
    case _ => this
  }

  abstract override def getUseScope: SearchScope = {
    def accessModifier(modifierListOwner: ScModifierListOwner) = Option(getModifierList).flatMap(_.accessModifier)

    def localSearchScope(typeDefinition: ScTypeDefinition, withCompanion: Boolean = true): SearchScope = {
      val scope = new LocalSearchScope(typeDefinition)
      if (withCompanion) {
        typeDefinition.baseCompanionModule match {
          case Some(td) => scope.union(new LocalSearchScope(td))
          case _ => scope
        }
      }
      else scope
    }

    def containingClassScope(withCompanion: Boolean) = containingClass match {
      case definition: ScTypeDefinition => Some(localSearchScope(definition, withCompanion))
      case _ => this.containingFile.map(new LocalSearchScope(_))
    }

    def forTopLevelPrivate(modifierListOwner: ScModifierListOwner) = modifierListOwner match {
      case td: ScTypeDefinition if td.isTopLevel =>
        val qName = Option(td.qualifiedName)
        val parentPackage = qName.flatMap(ScalaPsiUtil.parentPackage(_, td.getProject))
        parentPackage.map(new PackageScope(_, /*includeSubpackages*/ false, /*includeLibraries*/ true))
      case _ => None
    }

    @tailrec
    def fromContainingBlockOrMember(elem: PsiElement): Option[SearchScope] = {
      val blockOrMember = PsiTreeUtil.getContextOfType(elem, true, classOf[ScBlock], classOf[ScMember])
      blockOrMember match {
        case null => None
        case b: ScBlock => Some(new LocalSearchScope(b))
        case o: ScObject => Some(o.getUseScope)
        case td: ScTypeDefinition => //can't use td.getUseScope because of inheritance
          fromUnqualifiedOrThisPrivate(td) match {
            case None => fromContainingBlockOrMember(td)
            case scope => scope
          }
        case member: ScMember => Some(member.getUseScope)
      }
    }

    //should be checked only for the member itself
    //member of a qualified private class may escape it's package with inheritance
    def fromQualifiedPrivate(): Option[SearchScope] = {
      accessModifier(this).filter(am => am.isPrivate && !am.isUnqualifiedPrivateOrThis).map(_.scope) collect {
        case p: PsiPackage => new PackageScope(p, /*includeSubpackages*/true, /*includeLibraries*/true)
        case td: ScTypeDefinition => localSearchScope(td)
      }
    }

    def fromUnqualifiedOrThisPrivate(modifierListOwner: ScModifierListOwner) = {
      accessModifier(modifierListOwner) match {
        case Some(mod) if mod.isUnqualifiedPrivateOrThis =>
          forTopLevelPrivate(modifierListOwner).orElse {
            containingClassScope(withCompanion = !mod.isThis)
          }
        case _ => None
      }
    }

    def fromContext = this match {
      case cp: ScClassParameter =>
        Option(cp.containingClass).map {
          _.getUseScope
        }.orElse {
          Option(super.getUseScope)
        }
      case fun: ScFunction if fun.isSynthetic =>
        fun.getSyntheticNavigationElement.map {
          _.getUseScope
        }
      case _ =>
        fromQualifiedPrivate().orElse {
          fromContainingBlockOrMember(this)
        }
    }
    val fromModifierOrContext = fromUnqualifiedOrThisPrivate(this).orElse(fromContext)

    ScalaPsiUtil.intersectScopes(super.getUseScope, fromModifierOrContext)
  }
}

object ScMember {
  private val synthNavElemKey: Key[PsiElement] = Key.create("ScMember.synthNavElem")
  private val synthCaseClassKey: Key[ScClass] = Key.create("ScMember.synthCaseClass")
  private val synthContainingClassKey: Key[ScTypeDefinition] = Key.create("ScMember.synthContainingClass")
}
package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.util._
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.ModTracker
import org.jetbrains.plugins.scala.extensions.{ObjectExt, StubBasedExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScMemberOrLocal
import org.jetbrains.plugins.scala.macroAnnotations.Cached
import org.jetbrains.plugins.scala.util.BaseIconProvider

import scala.collection.mutable.ArrayBuffer

/**
  * @author Alexander Podkhalyuzin
  * Date: 04.05.2008
  */
trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {

  override def getContainingClass: PsiClass = containingClass

  final def syntheticNavigationElement: PsiElement =
    getUserData(syntheticNavigationElementKey)

  final def syntheticNavigationElement_=(navigationElement: PsiElement): Unit =
    putUserData(syntheticNavigationElementKey, navigationElement)

  final def syntheticContainingClass: ScTypeDefinition =
    getUserData(syntheticContainingClassKey)

  final def syntheticContainingClass_=(containingClass: ScTypeDefinition): Unit =
    putUserData(syntheticContainingClassKey, containingClass)

  final def originalEnumElement: ScEnum =
    getUserData(originalEnumElementKey)

  final def originalEnumElement_=(e: ScEnum): Unit =
    putUserData(originalEnumElementKey, e)

  /**
    * getContainingClassStrict(bar) == null in
    *
    * `object a { def foo { def bar = 0 }}`
    */
  @Cached(ModTracker.anyScalaPsiChange, this)
  @Nullable
  def containingClass: ScTemplateDefinition = {
    this match {
      case stub: StubBasedPsiElementBase[_] =>
        stub.getGreenStub match {
          case member: ScMemberOrLocal if member.isLocal => return null
          case _ =>
        }
      case _ =>
    }

    val found = getContainingClassLoose
    if (found == null) return null

    val clazz = ScMember.containingClass(this, found)
    if (clazz != null) return clazz

    val context = getContext
    val extendsBlock = found.extendsBlock
    val isCorrect = context != null &&
      (context == extendsBlock ||
        extendsBlock.templateBody.contains(context) ||
        extendsBlock.earlyDefinitions.contains(context) ||
        found.physicalExtendsBlock.templateBody.contains(context)) // in case a member is not present in the desugared extends block (e.g. deleted by a macro)

    if (isCorrect) found
    else null // See SCL-3178
  }

  def getContainingClassLoose: ScTemplateDefinition = {
    this.greenStub match {
      case Some(stub) => stub.getParentStubOfType(classOf[ScTemplateDefinition])
      case None =>
        this.child match {
          // TODO is all of this mess still necessary?!
          case c: ScClass if c.isCase =>
            this match {
              case fun: ScFunction if fun.isSynthetic && (fun.isApplyMethod || fun.isUnapplyMethod) =>
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

  def isLocal: Boolean = containingClass == null

  def isTopLevel: Boolean = getParent match {
    case _: ScPackaging | _: ScFile => true
    case _                          => false
  }

  def topLevelQualifier: Option[String] =
    PsiTreeUtil
      .getStubOrPsiParentOfType(this, classOf[ScPackaging])
      .toOption
      .map(_.fullPackageName)

  // TODO Should be unified, see ScModifierListOwner
  override def hasModifierProperty(name: String): Boolean = {
    def thisAccessModifier = getModifierList.accessModifier
      .filter(_.isThis)

    import PsiModifier._
    name match {
      case STATIC => containingClass.isInstanceOf[ScObject]
      case PUBLIC =>
        !hasModifierProperty(PRIVATE) &&
          !hasModifierProperty(PROTECTED)
      case PRIVATE => thisAccessModifier.exists(_.isPrivate)
      case PROTECTED => thisAccessModifier.exists(_.isProtected)
      case _ => getModifierList.hasModifierProperty(name)
    }
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
    case _: ScParameterClause =>
      this match {
        case cp: ScClassParameter =>
          Option(cp.containingClass)
            .flatMap(_.getNavigationElement.asOptionOf[ScConstructorOwner])
            .flatMap(_.parameters.find(_.name == cp.name))
            .getOrElse(this)
        case _ => this
      }
    case _ => this
  }
}

object ScMember {

  trait WithBaseIconProvider extends ScMember with BaseIconProvider {
    override protected final def delegate: ScMember = this
  }

  private val syntheticNavigationElementKey = Key.create[PsiElement]("ScMember.syntheticNavigationElement")

  private val syntheticContainingClassKey = Key.create[ScTypeDefinition]("ScMember.syntheticContainingClass")

  private val originalEnumElementKey = Key.create[ScEnum]("ScMember.originalEnumElement")

  private def containingClass(member: ScMember,
                              found: ScTemplateDefinition) = member match {
    case _: ScFunction |
         _: ScTypeDefinition =>
      member.syntheticContainingClass match {
        case null if member.isSynthetic => found
        case null => null
        case clazz => clazz
      }
    case _: ScTypeAlias |
         _: ScValueOrVariable => member.syntheticContainingClass
    case _: ScClassParameter | _: ScPrimaryConstructor => found
    case _ => null
  }

  implicit class ScMemberExt(private val member: ScMember) extends AnyVal {

    def isSynthetic: Boolean = member.syntheticNavigationElement != null

    def isPrivate: Boolean = member.hasModifierPropertyScala(PsiModifier.PRIVATE)

    def isProtected: Boolean = member.hasModifierPropertyScala(PsiModifier.PROTECTED)
  }
}
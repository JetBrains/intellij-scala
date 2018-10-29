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
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
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

  final def getSyntheticNavigationElement: Option[PsiElement] = Option(getUserData(synthNavElemKey))

  final def setSynthetic(navElement: PsiElement): Unit = putUserData(synthNavElemKey, navElement)

  final def isSynthetic: Boolean = getSyntheticNavigationElement.nonEmpty

  final def syntheticCaseClass: Option[ScClass] = Option(getUserData(synthCaseClassKey))

  final def setSyntheticCaseClass(cl: ScClass): Unit = putUserData(synthCaseClassKey, cl)

  final def syntheticContainingClass: Option[ScTypeDefinition] = Option(getUserData(synthContainingClassKey))

  final def setSyntheticContainingClass(td: ScTypeDefinition): Unit = putUserData(synthContainingClassKey, td)


  /**
    * getContainingClassStrict(bar) == null in
    *
    * `object a { def foo { def bar = 0 }}`
    */
  @Cached(ModCount.anyScalaPsiModificationCount, this)
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

  def isLocal: Boolean = containingClass == null

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
    case _ => this
  }
}

object ScMember {
  private val synthNavElemKey = Key.create[PsiElement]("ScMember.synthNavElem")
  private val synthCaseClassKey = Key.create[ScClass]("ScMember.synthCaseClass")
  private val synthContainingClassKey = Key.create[ScTypeDefinition]("ScMember.synthContainingClass")

  private def containingClass(member: ScMember,
                              found: ScTemplateDefinition) = member match {
    case fun: ScFunction if fun.syntheticContainingClass.isDefined => fun.syntheticContainingClass.get
    case fun: ScFunction if fun.isSynthetic => found
    case ta: ScTypeAlias if ta.syntheticContainingClass.isDefined => ta.syntheticContainingClass.get
    case td: ScTypeDefinition if td.syntheticContainingClass.isDefined => td.syntheticContainingClass.get
    case td: ScTypeDefinition if td.isSynthetic => found
    case valVar: ScValueOrVariable if valVar.syntheticContainingClass.isDefined => valVar.syntheticContainingClass.get
    case _: ScClassParameter | _: ScPrimaryConstructor => found
    case _ => null
  }
}
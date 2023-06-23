package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.openapi.util.Key
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement, PsiMember, PsiModifier}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions.{&, ObjectExt, Parent, StubBasedExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.api.{ScFile, ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaFileImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScMemberOrLocal
import org.jetbrains.plugins.scala.util.BaseIconProvider

trait ScMember extends ScalaPsiElement with ScModifierListOwner with PsiMember {

  import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember._

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

  final def originalGivenElement: ScGivenDefinition =
    getUserData(originalGivenElementKey)

  final def originalGivenElement_=(e: ScGivenDefinition): Unit =
    putUserData(originalGivenElementKey, e)

  /**
    * getContainingClassStrict(bar) == null in
    *
    * `object a { def foo { def bar = 0 }}`
    */
  @Nullable
  def containingClass: ScTemplateDefinition = _containingClass()

  private val _containingClass = cached("containingClass", ModTracker.anyScalaPsiChange, () => {
    containingClass0
  })

  private def containingClass0: ScTemplateDefinition = {
    this match {
      case stub: StubBasedPsiElementBase[_] =>
        stub.getGreenStub match {
          case member: ScMemberOrLocal[_] if member.isLocal =>
            return null
          case _ =>
        }
      case _ =>
    }

    val found = getContainingClassLoose
    if (found == null)
      return null

    val clazz = ScMember.containingClass(this, found)
    if (clazz != null)
      return clazz

    val context = getContext
    val extendsBlock = found.extendsBlock

    def checkContext(ctx: PsiElement) = ctx != null &&
      (ctx == extendsBlock ||
        extendsBlock.templateBody.contains(ctx) ||
        extendsBlock.earlyDefinitions.contains(ctx) ||
        found.extendsBlock.templateBody.contains(ctx)) // in case a member is not present in the desugared extends block (e.g. deleted by a macro)

    val isCorrectExtension = context match {
      case eb: ScExtensionBody =>
        //if this is an extension method, check context of an extension itself
        checkContext(eb.getContext.getContext)
      case _                  => false
    }

    if (checkContext(context)) found
    else if (isCorrectExtension) found
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
                  case Some(td) =>
                    return td
                  case _ =>
                }
              case _ =>
            }
          case _ =>
        }
        PsiTreeUtil.getContextOfType(this, true, classOf[ScTemplateDefinition])
    }
  }

  def isLocal: Boolean = containingClass == null && !isTopLevel && {
    val parent = getParent
    val isExtensionMethod = parent.is[ScExtensionBody]
    !isExtensionMethod
  }

  def isDefinedInClass: Boolean = containingClass != null

  def isTopLevel: Boolean = getContext match {
    case _: ScPackaging | _: ScFile => true
    case _                          => false
  }

  /**
   * @return Some package name in case member is a top level definition<br>
   *         None otherwise
   */
  def topLevelQualifier: Option[String] = {
    val parent = PsiTreeUtil.getStubOrPsiParent(this)
    parent match {
      case p: ScPackaging => Some(p.fullPackageName)
      case _: ScalaFile => Some("") //default package
      case _ => None
    }
  }

  // TODO Should be unified, see ScModifierListOwner
  override def hasModifierProperty(name: String): Boolean = {
    import PsiModifier._
    if (name == STATIC)
      containingClass.isInstanceOf[ScObject]
    else {
      val modifierList = getModifierList
      name match {
        case PUBLIC =>
          val hasPrivateOrProtected = modifierList.accessModifier.exists(_.isPrivate) ||
            modifierList.accessModifier.exists(_.isProtected)
          !hasPrivateOrProtected
        case PRIVATE =>
          //private modifier at top level in Scala is equivalent to package private visibility scope in Java
          //This can matter e.g. when printing UAST tree as Java code
          //(see `PsiModifierListOwner.renderModifiers` in `org.jetbrains.uast/internalUastUtils.kt`)
          if (isTopLevel)
            false
          else
            modifierList.accessModifier.exists(_.isPrivate)
        case PROTECTED =>
          modifierList.accessModifier.exists(_.isProtected)
        case _ =>
          modifierList.hasModifierProperty(name)
      }
    }
  }

  /**
   * @param isStrictCheck false - Fast option to just check member names<br>
   *                      true - Slow option which will check all function overloaded alternatives
   *                      For that it needs to resolve all type paramers and check parameter types equivalence
   */
  protected def isSimilarMemberForNavigation(m: ScMember, isStrictCheck: Boolean) = false

  override def getNavigationElement: PsiElement = getContainingFile match {
    case s: ScalaFileImpl if s.isCompiled =>
      getSourceMirrorMember
    case _ => this
  }

  private def getSourceMirrorMember: ScMember = getParent match {
    case (_: ScTemplateBody) & Parent((_: ScExtendsBlock) & Parent(td: ScTypeDefinition)) =>
      val navigationElement = td.getNavigationElement
      navigationElement match {
        case typeDefinition: ScTypeDefinition =>
          val membersIterator = typeDefinition.members.iterator

          //use fast check to find candidates with matching name
          val similarMembersFast = membersIterator.filter(isSimilarMemberForNavigation(_, isStrictCheck = false)).toSeq
          if (similarMembersFast.isEmpty)
            this
          else if (similarMembersFast.length == 1)
            similarMembersFast.head
          else {
            //multiple candidates (most likely overloaded functions), need to check signatures (slow check)
            val similarMemberWithStrictCheck = similarMembersFast.find(isSimilarMemberForNavigation(_, isStrictCheck = true))
            similarMemberWithStrictCheck.getOrElse(similarMembersFast.head)
          }
        case _ => this
      }
    case c: ScTypeDefinition if this.isInstanceOf[ScPrimaryConstructor] => //primary constructor
      val navigationElement = c.getNavigationElement
      navigationElement match {
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
          val containingClass = Option(cp.containingClass)
          val navigationElement = containingClass.flatMap(_.getNavigationElement.asOptionOf[ScConstructorOwner])
          navigationElement
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
  private val syntheticContainingClassKey   = Key.create[ScTypeDefinition]("ScMember.syntheticContainingClass")
  private val originalEnumElementKey        = Key.create[ScEnum]("ScMember.originalEnumElement")
  private val originalGivenElementKey       = Key.create[ScGivenDefinition]("ScMember.originalGivenElement")

  private def containingClass(member: ScMember, found: ScTemplateDefinition) = member match {
    case _: ScFunction | _: ScTypeDefinition =>
      member.syntheticContainingClass match {
        case null if member.isSynthetic => found
        case null                       => null
        case clazz                      => clazz
      }
    case _: ScTypeAlias | _: ScValueOrVariable         => member.syntheticContainingClass
    case _: ScClassParameter | _: ScPrimaryConstructor => found
    case _                                             => null
  }

  implicit class ScMemberExt(private val member: ScMember) extends AnyVal {

    def isSynthetic: Boolean = member.syntheticNavigationElement != null

    def isPrivate: Boolean = member.hasModifierPropertyScala(PsiModifier.PRIVATE)

    def isProtected: Boolean = member.hasModifierPropertyScala(PsiModifier.PROTECTED)
  }
}
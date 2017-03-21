package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.util.Iconable
import com.intellij.psi._
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createObjectWithContext, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.Seq

/**
 * @author AlexanderPodkhalyuzin
 */

trait ScTypeDefinition extends ScTemplateDefinition with ScMember
    with NavigationItem with PsiClass with ScTypeParametersOwner with Iconable with ScDocCommentOwner
    with ScAnnotationsHolder with ScCommentOwner {

  def isCase: Boolean = false

  def isObject: Boolean = false

  def isTopLevel: Boolean = !this.parentsInFile.exists(_.isInstanceOf[ScTypeDefinition])

  def getPath: String = {
    val qualName = qualifiedName
    val index = qualName.lastIndexOf('.')
    if (index < 0) "" else qualName.substring(0, index)
  }

  def getQualifiedNameForDebugger: String

  /**
   * Qualified name stops on outer Class level.
   */
  def getTruncedQualifiedName: String

  def signaturesByName(name: String): Seq[PhysicalSignature]

  def isPackageObject = false

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitTypeDefinition(this)
  }

  def getObjectClassOrTraitToken: PsiElement

  def getSourceMirrorClass: PsiClass

  override def isEquivalentTo(another: PsiElement): Boolean = {
    PsiClassImplUtil.isClassEquivalentTo(this, another)
  }

  def allInnerTypeDefinitions: Seq[ScTypeDefinition] = members.collect {
    case td: ScTypeDefinition => td
  }

  override def syntheticTypeDefinitionsImpl: Seq[ScTypeDefinition] = SyntheticMembersInjector.injectInners(this)

  override def syntheticMembersImpl: Seq[ScMember] = SyntheticMembersInjector.injectMembers(this)

  override protected def syntheticMethodsWithOverrideImpl: scala.Seq[PsiMethod] = SyntheticMembersInjector.inject(this, withOverride = true)

  def fakeCompanionModule: Option[ScObject] = {
    if (this.isInstanceOf[ScObject]) return None
    baseCompanionModule match {
      case Some(_: ScObject) => return None
      case _ if !isCase && !SyntheticMembersInjector.needsCompanion(this) => return None
      case _ =>
    }

    calcFakeCompanionModule()
  }

  override def showAsInheritor: Boolean = true

  //Performance critical method
  //And it is REALLY SO!
  def baseCompanionModule: Option[ScTypeDefinition] = {
    val scope = getContext
    if (scope == null) return None

    val thisName: String = name
    val tokenSet = TokenSets.TYPE_DEFINITIONS
    val stub = scope match {
      case stub: ScalaStubBasedElementImpl[_, _] => Option(stub.getStub)
      case file: PsiFileImpl => Option(file.getStub)
      case _ => None
    }
    val arrayOfElements: Array[PsiElement] =
      stub.map(_.getChildrenByType(tokenSet, JavaArrayFactoryUtil.PsiElementFactory))
        .getOrElse(scope.getChildren)

    val length  = arrayOfElements.length
    this match {
      case _: ScClass | _: ScTrait =>
        var i = 0
        while (i < length) {
          arrayOfElements(i) match {
            case obj: ScObject if obj.name == thisName => return Some(obj)
            case _ =>
          }
          i = i + 1
        }
        None
      case _: ScObject =>
        var i = 0
        val length  = arrayOfElements.length
        while (i < length) {
          arrayOfElements(i) match {
            case c: ScClass if c.name == thisName => return Some(c)
            case t: ScTrait if t.name == thisName => return Some(t)
            case _ =>
          }
          i = i + 1
        }
        None
      case _ => None
    }
  }

  @Cached(synchronized = true, ModCount.getBlockModificationCount, this)
  def calcFakeCompanionModule(): Option[ScObject] = {
    val accessModifier = getModifierList.accessModifier.fold("")(_.modifierFormattedText + " ")
    val objText = this match {
      case clazz: ScClass if clazz.isCase =>
        val texts = clazz.getSyntheticMethodsText

        val extendsText = {
          try {
            if (typeParameters.isEmpty && clazz.constructor.get.effectiveParameterClauses.length == 1) {
              val typeElementText =
                clazz.constructor.get.effectiveParameterClauses.map {
                  clause =>
                    clause.effectiveParameters.map(parameter => {
                      val parameterText = parameter.typeElement.fold("_root_.scala.Nothing")(_.getText)
                      if (parameter.isRepeatedParameter) s"_root_.scala.Seq[$parameterText]"
                      else parameterText
                    }).mkString("(", ", ", ")")
                }.mkString("(", " => ", s" => $name)")
              val typeElement = createTypeElementFromText(typeElementText)
              s" extends ${typeElement.getText}"
            } else {
              ""
            }
          } catch {
            case _: Exception => ""
          }
        }

        s"""${accessModifier}object ${clazz.name}$extendsText{
           |  ${texts.mkString("\n  ")}
           |}""".stripMargin
      case _ =>
        s"""${accessModifier}object $name {
           |  //Generated synthetic object
           |}""".stripMargin
    }


    val next = ScalaPsiUtil.getNextStubOrPsiElement(this)
    val obj: ScObject =
      createObjectWithContext(objText, getContext, if (next != null) next else this)
    import org.jetbrains.plugins.scala.extensions._
    val objOption: Option[ScObject] = obj.toOption
    objOption.foreach { (obj: ScObject) =>
      obj.setSyntheticObject()
      obj.extendsBlock.members.foreach {
        case s: ScFunctionDefinition =>
          s.setSynthetic(this) // So we find the `apply` method in ScalaPsiUtil.syntheticParamForParam
          this match {
            case clazz: ScClass if clazz.isCase =>
              s.setSyntheticCaseClass(clazz)
            case _ =>
          }
        case _ =>
      }
    }
    objOption
  }

}

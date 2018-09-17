package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package typedef

import com.intellij.navigation.NavigationItem
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Iconable
import com.intellij.psi._
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.impl.source.PsiFileImpl
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.{createObjectWithContext, createTypeElementFromText}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.SyntheticMembersInjector
import org.jetbrains.plugins.scala.lang.psi.types.PhysicalSignature
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import scala.collection.Seq

/**
 * @author AlexanderPodkhalyuzin
 */

trait ScTypeDefinition extends ScTemplateDefinition with ScMember
    with NavigationItem with PsiClassAdapter with ScTypeParametersOwner with Iconable with ScDocCommentOwner
    with ScCommentOwner {

  override def extendsBlock = super.extendsBlock

  def isCase: Boolean = false

  def isObject: Boolean = false

  def isTopLevel: Boolean = !this.parentsInFile.exists(_.isInstanceOf[ScTypeDefinition])

  def getPath: String = {
    val qualName = qualifiedName
    val index = qualName.lastIndexOf('.')
    if (index < 0) "" else qualName.substring(0, index)
  }

  def getQualifiedNameForDebugger: String

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

  override def typeParameters: Seq[ScTypeParam]

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
    val isObject = this match {
      case _: ScObject => true
      case _: ScTrait | _: ScClass => false
      case _ => return None
    }

    val thisName: String = name

    def isCompanion(td: ScTypeDefinition): Boolean = td match {
      case td @ (_: ScClass | _: ScTrait)
        if isObject && td.asInstanceOf[ScTypeDefinition].name == thisName => true
      case o: ScObject if !isObject && thisName == o.name => true
      case _ => false
    }

    var sibling: PsiElement = this
    while (sibling != null) {

      sibling = sibling.getNextSibling

      sibling match {
        case td: ScTypeDefinition if isCompanion(td) => return Some(td)
        case _ =>
      }
    }

    sibling = this
    while (sibling != null) {

      sibling = sibling.getPrevSibling

      sibling match {
        case td: ScTypeDefinition if isCompanion(td) => return Some(td)
        case _ =>
      }
    }

    None
  }

  @Cached(CachesUtil.libraryAwareModTracker(this), this)
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
            case p: ProcessCanceledException => throw p
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
      obj.physicalExtendsBlock.members.foreach {
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

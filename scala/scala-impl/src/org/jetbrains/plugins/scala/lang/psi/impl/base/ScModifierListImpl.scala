package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/
class ScModifierListImpl private (stub: ScModifiersStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.MODIFIERS, node) with ScModifierList {

  import PsiModifier._
  import ScModifierList._
  import NonAccessModifier._

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScModifiersStub) = this(stub, null)

  override def toString: String = "Modifiers"

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  override def modifiers: Set[String] = byStubOrPsi(_.modifiers.toSet) {
    val result = mutable.HashSet.empty[String]

    for {
      Val(keyword, prop) <- values
      if findFirstChildByType(prop) != null
    } result += keyword

    result += accessModifier.fold(PUBLIC) {
      case modifier if modifier.isPrivate => PRIVATE
      case _ => PROTECTED
    }

    result.toSet
  }

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  override def accessModifier: Option[ScAccessModifier] = Option {
    getStubOrPsiChild(ScalaElementTypes.ACCESS_MODIFIER)
  }

  override def hasExplicitModifiers: Boolean = byStubOrPsi(_.hasExplicitModifiers) {
    findChildByType(Modifiers) != null
  }

  override def setModifierProperty(name: String, value: Boolean): Unit = {
    checkSetModifierProperty(name, value)
    hasModifierProperty(name) match {
      case `value` =>
      case _ if value =>
        val isValid = name match {
          case PRIVATE | PROTECTED => true
          case _ =>
            values.exists {
              case Val(`name`, _) => true
              case _ => false
            }
        }

        if (isValid) addModifierProperty(name)
      case _ =>
        def withAccessModifier(predicate: ScAccessModifier => Boolean) =
          getChildren.collectFirst {
            case modifier: ScAccessModifier if predicate(modifier) => modifier
          }

        val maybeElement = name match {
          case PRIVATE => withAccessModifier(_.isPrivate)
          case PROTECTED => withAccessModifier(_.isProtected)
          case _ =>
            values.collectFirst {
              case Val(`name`, prop) => findChildByType(prop)
            }
        }

        for {
          element <- maybeElement
          node = element.getNode
        } getNode.removeChild(node)
    }
  }

  override def getAnnotations: Array[PsiAnnotation] = getParent match {
    case null => PsiAnnotation.EMPTY_ARRAY
    case parent =>
      parent.stubOrPsiChildren(
        ScalaElementTypes.ANNOTATIONS,
        JavaArrayFactoryUtil.ScAnnotationsFactory
      ) match {
        case Array() => PsiAnnotation.EMPTY_ARRAY
        case Array(head, _@_*) =>
          val scAnnotations = head.getAnnotations
          val result = PsiAnnotation.ARRAY_FACTORY.create(scAnnotations.length)
          scAnnotations.copyToArray(result)
          result
      }
  }

  override def findAnnotation(name: String): PsiAnnotation = getAnnotations.find {
    _.getQualifiedName == name
  }.getOrElse {
    name match {
      case "java.lang.Override" =>
        JavaPsiFacade.getInstance(getProject).getElementFactory
          .createAnnotationFromText("@" + name, this); // hack to disable AddOverrideAnnotationAction,
      case _ => null
    }
  }

  override def accept(visitor: ScalaElementVisitor): Unit =
    visitor.visitModifierList(this)

  override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case scalaVisitor: ScalaElementVisitor => accept(scalaVisitor)
    case _ => super.accept(visitor)
  }

  private def addModifierProperty(name: String): Unit = {
    val node = getNode
    val modifierNode = createModifierFromText(name).getNode

    val addAfter = name != Case.keyword
    val spaceNode = createNewLineNode(" ")

    getFirstChild match {
      case null if addAfter =>
        val parentNode = getParent.getNode
        var nextSibling = getNextSibling
        while (ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(nextSibling.getNode.getElementType)) {
          val currentNode = nextSibling.getNode
          nextSibling = nextSibling.getNextSibling

          parentNode.removeChild(currentNode)
          parentNode.addChild(currentNode, node)
        }

        node.addChild(modifierNode)
        parentNode.addChild(spaceNode, nextSibling.getNode)
      case null =>
        node.addChild(modifierNode)
        node.addChild(spaceNode)
      case first if addAfter =>
        val firstNode = first.getNode
        node.addChild(modifierNode, firstNode)
        node.addChild(spaceNode, firstNode)
      case _ =>
        node.addChild(spaceNode)
        node.addChild(modifierNode)
    }
  }
}
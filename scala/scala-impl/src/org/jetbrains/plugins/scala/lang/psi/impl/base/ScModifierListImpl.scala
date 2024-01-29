package org.jetbrains.plugins.scala.lang.psi.impl.base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.extensions.{ElementType, StubBasedExt}
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaModifierTokenType, ScalaTokenTypes}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub
import org.jetbrains.plugins.scala.util.EnumSet
import org.jetbrains.plugins.scala.util.EnumSet._

class ScModifierListImpl private (stub: ScModifiersStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.MODIFIERS, node) with ScModifierList {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScModifiersStub) = this(stub, null)

  override def toString: String = "Modifiers"

  override def modifiers: EnumSet[ScalaModifier] = byStubOrPsi(_.modifiers) {
    var result = EnumSet.empty[ScalaModifier]

    //this method is optimized to avoid creation of unnecessary arrays

    var currentChild = getFirstChild

    while (currentChild != null) {
      currentChild match {
        case a: ScAccessModifier =>
          result ++= (if (a.isPrivate) ScalaModifier.Private else ScalaModifier.Protected)
        case ElementType(ScalaModifierTokenType(mod)) =>
          result ++= mod
        case _ =>
      }
      currentChild = currentChild.getNextSibling
    }

    result
  }

  override def modifiersOrdered: Seq[ScalaModifier] = {
    val builder = Seq.newBuilder[ScalaModifier]

    var currentChild = getFirstChild

    while (currentChild != null) {
      currentChild match {
        case a: ScAccessModifier =>
          builder += (if (a.isPrivate) ScalaModifier.Private else ScalaModifier.Protected)
        case ElementType(ScalaModifierTokenType(mod)) =>
          builder += mod
        case _ =>
      }
      currentChild = currentChild.getNextSibling
    }

    builder.result()
  }

  override def hasModifierProperty(name: String): Boolean = {
    if (name == PsiModifier.PUBLIC)
      !modifiers.contains(ScalaModifier.Private) && !modifiers.contains(ScalaModifier.Protected)
    else
      hasExplicitModifier(name)
  }

  override def accessModifier: Option[ScAccessModifier] = Option {
    getStubOrPsiChild(ScalaElementType.ACCESS_MODIFIER)
  }

  override def setModifierProperty(name: String, value: Boolean): Unit = {
    checkSetModifierProperty(name, value)

    if (name == PsiModifier.PUBLIC) {
      if (value) {
        setModifierProperty(PsiModifier.PRIVATE, value = false)
        setModifierProperty(PsiModifier.PROTECTED, value = false)
        return
      }
      else {
        //not supported
      }
    }

    val isReplacement = (hasExplicitModifier(PsiModifier.PROTECTED) && name == PsiModifier.PRIVATE) ||
      (hasExplicitModifier(PsiModifier.PRIVATE) && name == PsiModifier.PROTECTED)

    val mod = ScalaModifier.byText(name)

    if (mod == ScalaModifier.Private || mod == ScalaModifier.Protected) {
      accessModifier.foreach(e => getNode.removeChild(e.getNode))
    } else if (mod == null || value == modifiers.contains(mod)) {
      return
    }

    if (value) {
      addModifierProperty(name, isReplacement)
    } else {
      val elemToRemove: Option[PsiElement] = mod match {
        case ScalaModifier.Private if accessModifier.exists(_.isPrivate)     => accessModifier
        case ScalaModifier.Protected if accessModifier.exists(_.isProtected) => accessModifier
        case _                                                               => Option(findChildByType(ScalaModifierTokenType(mod)))
      }
      elemToRemove.foreach(e => getNode.removeChild(e.getNode))
    }
  }

  //TODO: to be more consistent with getApplicableAnnotations in 2021.2 lets change it to simpler implementation:
  // (investigate caching)
  // getParent match {
  //   case owner: ScAnnotationsHolder => owner.getAnnotations
  //   case _ => PsiAnnotation.EMPTY_ARRAY
  // }
  override def getAnnotations: Array[PsiAnnotation] = getParent match {
    case null => PsiAnnotation.EMPTY_ARRAY
    case parent =>
      parent.stubOrPsiChildren(
        ScalaElementType.ANNOTATIONS,
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

  override def getApplicableAnnotations: Array[PsiAnnotation] =
    getParent match {
      case owner: ScAnnotationsHolder =>
        owner.getApplicableAnnotations
      case _ =>
        PsiAnnotation.EMPTY_ARRAY
    }

  override def addAnnotation(qualifiedName: String): PsiAnnotation = {
    getParent match {
      case owner: ScAnnotationsHolder =>
        owner.addAnnotation(qualifiedName)
      case _ => //see contract of base method
        throw new UnsupportedOperationException(s"Can't add annotation to modifier list of ${this.getParent.getClass}")
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

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitModifierList(this)

  override def hasExplicitModifier(name: String): Boolean = {
    val mod = ScalaModifier.byText(name)
    mod != null && modifiers.contains(mod)
  }

  override def checkSetModifierProperty(name: String, value: Boolean): Unit = {}

  private def addModifierProperty(name: String, isReplacement: Boolean): Unit = {
    val node = getNode
    val modifierNode = createModifierFromText(name).getNode

    val addAfter = name != ScalaModifier.CASE
    val spaceNode = createNewLineNode(" ")

    getFirstChild match {
      case null if addAfter =>

        val parentNode = getParent.getNode
        var nextSibling = getNextSibling

        if (!isReplacement) {

          while (nextSibling != null &&
            ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(nextSibling.getNode.getElementType)) {
            val currentNode = nextSibling.getNode
            nextSibling = nextSibling.getNextSibling

            parentNode.removeChild(currentNode)
            parentNode.addChild(currentNode, node)
          }
        }

        node.addChild(modifierNode)

        if (!isReplacement && nextSibling != null)
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
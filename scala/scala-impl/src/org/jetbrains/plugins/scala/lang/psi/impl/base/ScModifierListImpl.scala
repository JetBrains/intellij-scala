package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScAnnotationsFactory
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.TokenSets.TokenSetExt
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScModifiersStub
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
*/
class ScModifierListImpl private (stub: ScModifiersStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.MODIFIERS, node) with ScModifierList {

  import ScModifierListImpl._

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScModifiersStub) = this(stub, null)

  override def toString: String = "Modifiers"

  def getApplicableAnnotations: Array[PsiAnnotation] = PsiAnnotation.EMPTY_ARRAY

  override def hasModifierProperty(name: String): Boolean = modifiers.contains(name)

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  def modifiers: Seq[String] = byStubOrPsi(_.modifiers.toSeq) {
    val maybePublicModifier = if (access.isDefined) None
    else Some(PsiModifier.PUBLIC)

    val modifiers = AllModifiers.collect {
      case (prop, string) if has(prop) => string
    }

    (modifiers ++ maybePublicModifier).toSeq
  }

  @Cached(ModCount.anyScalaPsiModificationCount, this)
  def accessModifier: Option[ScAccessModifier] = Option(getStubOrPsiChild(ScalaElementTypes.ACCESS_MODIFIER))

  def hasExplicitModifiers: Boolean = byStubOrPsi(_.hasExplicitModifiers) {
    !findChildrenByType(TokenSets.MODIFIERS + ScalaElementTypes.ACCESS_MODIFIER).isEmpty
  }

  def hasExplicitModifier(name: String) = false

  def setModifierProperty(name: String, value: Boolean) {
    def space = createNewLineNode(" ")
    checkSetModifierProperty(name, value)
    if (hasModifierProperty(name) == value) return

    def addAfter(modifier: String): Unit = {
      val wasEmpty = getFirstChild == null
      if (!wasEmpty) getNode.addChild(space)
      getNode.addChild(createModifierFromText(modifier).getNode)
      if (wasEmpty) getNode.addChild(space)
    }

    def addBefore(modifier: String): Unit = {
      val node = createModifierFromText(modifier).getNode

      val first = getFirstChild
      if (first == null) {
        val buf = new ArrayBuffer[ASTNode]()
        var nextSibling = getNextSibling
        while (ScalaTokenTypes.WHITES_SPACES_AND_COMMENTS_TOKEN_SET.contains(nextSibling.getNode.getElementType)) {
          buf += nextSibling.getNode
          nextSibling = nextSibling.getNextSibling
        }

        val parent = getParent
        for (node <- buf) {
          parent.getNode.removeChild(node)
          parent.getNode.addChild(node, getNode)
        }
        getNode.addChild(node)
        parent.getNode.addChild(space, nextSibling.getNode)
        return
      }
      getNode.addChild(node, first.getNode)
      getNode.addChild(space, first.getNode)
    }
    name match {
      case "override" => if (value) {
        addBefore("override")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kOVERRIDE).getNode)
      case "private" => if (value) {
        addBefore("private")
      }
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isPrivate) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "protected" => if (value) {
        addBefore("protected")
      }
        else {
        for (child <- getChildren if child.isInstanceOf[ScAccessModifier] && child.asInstanceOf[ScAccessModifier].isProtected) {
          getNode.removeChild(child.getNode)
          return
        }
      }
      case "final" => if (value) {
        addBefore("final")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kFINAL).getNode)
      case "implicit" => if (value) {
        addBefore("implicit")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kIMPLICIT).getNode)
      case "abstract" => if (value) {
        addBefore("abstract")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kABSTRACT).getNode)
      case "sealed" => if (value) {
        addBefore("sealed")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kSEALED).getNode)
      case "lazy" => if (value) {
        addBefore("lazy")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kLAZY).getNode)
      case "case" => if (value) {
        addAfter("case")
      }
        else getNode.removeChild(findChildByType[PsiElement](ScalaTokenTypes.kCASE).getNode)
      case _ =>
    }
  }

  def checkSetModifierProperty(name: String, value: Boolean) {
    //todo implement me!
  }

  def getAnnotations: Array[PsiAnnotation] = {
    getParent match {
      case null => PsiAnnotation.EMPTY_ARRAY
      case parent =>
        val annotations = parent.stubOrPsiChildren(ScalaElementTypes.ANNOTATIONS, ScAnnotationsFactory)
        if (annotations.length == 0) PsiAnnotation.EMPTY_ARRAY
        else {
          val scAnnotations = annotations(0).getAnnotations
          val result = PsiAnnotation.ARRAY_FACTORY.create(scAnnotations.length)
          scAnnotations.copyToArray(result)
          result
        }
    }
  }

  def findAnnotation(name: String): PsiAnnotation = {
    getAnnotations.find(_.getQualifiedName == name) match {
      case None if name == "java.lang.Override" =>
        val factory = JavaPsiFacade.getInstance(getProject).getElementFactory
        factory.createAnnotationFromText("@" + name, this); // hack to disable AddOverrideAnnotationAction, 
      case None => null
      case Some(x) => x
    }
  }

  def has(prop: IElementType): Boolean = {
    import ScAccessModifier.Type._
    prop match {
      case ScalaTokenTypes.kPRIVATE => access.exists {
        case PRIVATE | THIS_PRIVATE => true
        case _ => false
      }
      case ScalaTokenTypes.kPROTECTED => access.exists {
        case PROTECTED | THIS_PROTECTED => true
        case _ => false
      }
      case _ =>
        byStubOrPsi(_.modifiers.contains(AllModifiers(prop)))(findChildByType(prop) != null)
    }
  }

  def addAnnotation(qualifiedName: String): PsiAnnotation = null

  override def accept(visitor: ScalaElementVisitor): Unit =
    visitor.visitModifierList(this)

  override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case scalaVisitor: ScalaElementVisitor => accept(scalaVisitor)
    case _ => super.accept(visitor)
  }

  private def access = accessModifier.map(_.access)
}

object ScModifierListImpl {

  private val AllModifiers = Map(
    ScalaTokenTypes.kOVERRIDE -> "override",
    ScalaTokenTypes.kPRIVATE -> "private",
    ScalaTokenTypes.kPROTECTED -> "protected",
    ScalaTokenTypes.kFINAL -> "final",
    ScalaTokenTypes.kIMPLICIT -> "implicit",
    ScalaTokenTypes.kABSTRACT -> "abstract",
    ScalaTokenTypes.kSEALED -> "sealed",
    ScalaTokenTypes.kLAZY -> "lazy",
    ScalaTokenTypes.kCASE -> "case"
  )
}
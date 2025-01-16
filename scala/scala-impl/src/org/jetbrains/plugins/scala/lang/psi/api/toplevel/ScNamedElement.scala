package org.jetbrains.plugins.scala.lang.psi.api.toplevel

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.stubs.{NamedStub, StubElement}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.caches.{ModTracker, cached}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.isNameContext
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScCaseClause
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement.{AnonymousPlaceholder, NameId}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createIdentifier
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.JavaIdentifier
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import javax.swing.Icon
import scala.annotation.tailrec

trait ScNamedElement extends ScalaPsiElement
  with PsiNameIdentifierOwner
  with NavigatablePsiElement
  with PsiNamedElementWithCustomPresentation
{

  def name: String = _name()

  private val _name = cached("name", ModTracker.anyScalaPsiChange, () => {
    this match {
      case st: StubBasedPsiElementBase[_] => st.getGreenStub match {
        case namedStub: NamedStub[_] => namedStub.getName
        case _ => nameId.forcedName
      }
      case _ => nameId.forcedName
    }
  })

  override def getPresentationName: String = name

  def nameContext: PsiElement = _nameContext()

  private val _nameContext = cached("nameContext", ModTracker.anyScalaPsiChange, () => {
    @tailrec
    def byStub(stub: StubElement[_]): PsiElement = {
      if (stub == null) null
      else {
        val psi = stub.getPsi.asInstanceOf[PsiElement]

        if (isNameContext(psi)) psi
        else byStub(stub.getParentStub)
      }
    }

    @tailrec
    def byAST(element: PsiElement): PsiElement =
      if (element == null || isNameContext(element)) element
      else byAST(element.getParent)

    this match {
      case st: StubBasedPsiElementBase[_] =>
        val stub = st.getStub.asInstanceOf[StubElement[_]]

        if (stub != null) byStub(stub)
        else byAST(this)
      case _ => byAST(this)
    }
  })

  override def getTextOffset: Int = {
    val nameId = this.nameId.forNavigation
    val range = if (nameId != null) nameId.getTextRange else getTextRange
    range.getStartOffset
  }

  override def getName: String = ScalaNamesUtil.toJavaName(name)

  /**
   * PsiElement representing a name identifier
   *
   * @note can be `null` in some cases<br>
   *       '''Example 1''' - anonymous context parameter {{{
   *         def foo(using String): Unit = ()
   *       }}}
   *       '''Example 2''' - anonymous given declaration/definition/structural instance {{{
   *         given String
   *         given String = ???
   *         given MyType with MyTrait with {}
   *       }}}
   *       '''Example 3''' - anonymous class (new template definition) {{{
   *         new Object() {
   *         }
   *       }}}
   */
  def nameId: NameId

  override def getNameIdentifier: PsiIdentifier =
    nameId.explicitIdentifier.map(new JavaIdentifier(_)).orNull

  override def setName(name: String): PsiElement = nameId.explicitIdentifier match {
    case Some(nameId) =>
      val id = nameId.getNode
      val parent = id.getTreeParent
      val newId = createIdentifier(name)
      parent.replaceChild(id, newId)
      this
    case None =>
      throw new IncorrectOperationException(s"Cannot rename anonymous element of type ${this.getClass.getSimpleName}")
  }

  override def getPresentation: ItemPresentation = {
    val _nameContext = nameContext
    val classContainer: ScTemplateDefinition = if (_nameContext == null) null else {
      val parent = _nameContext.getParent
      if (parent.is[ScTemplateBody, ScEarlyDefinitions] || this.is[ScClassParameter])
        PsiTreeUtil.getParentOfType(this, classOf[ScTemplateDefinition], true)
      else
        null
    }
    val parentMember = Option(PsiTreeUtil.getParentOfType(this, classOf[ScMember], false))
    new ItemPresentation {
      override def getPresentableText: String = name
      override def getLocationString: String = classContainer match {
        case _: ScTypeDefinition => "(" + classContainer.qualifiedName + ")"
        case _: ScNewTemplateDefinition => s"($AnonymousPlaceholder)"
        case _ =>
          //Note, the location of the parent member and name context member can be different.
          //When we have a top-level definition, we use the top-level qualifier.
          //When we have some local element, we just show the closest member text.
          val maybeTopLevelQualifier = _nameContext match {
            case member: ScMember => member.topLevelQualifier
            case _ => None
          }
          maybeTopLevelQualifier.getOrElse {
            val parentMemberTextShort = parentMember.map(m => StringUtil.first(m.getText, 30, true))
            parentMemberTextShort.getOrElse("")
          }
      }
      override def getIcon(open: Boolean): Icon = parentMember.map(_.getIcon(0)).orNull
    }
  }

  override def getIcon(flags: Int): Icon =
    nameContext match {
      case null => null
      case _: ScCaseClause => Icons.PATTERN_VAL
      case x => x.getIcon(flags)
    }
}

object ScNamedElement {
  val AnonymousPlaceholder = "<anonymous>"
  val MissingNamePlaceholder = "<missing name>"

  abstract class NameId {
    // the name with which the corresponding ScNamedElement can be referenced (so it's None if the element is anonymous)
    def name: Option[String]
    // Whether the corresponding ScNamedElement can be referenced with a name (exactly true iff name is None)
    def isAnonymous: Boolean
    // An actual name written by the user (So None for anonymous elements or givens without explicit names)
    def explicitName: Option[String]
    // use this if you need some kind of name-representation
    def forcedName: String
    // An element that should be highlighted if the name should be highlighted
    def forHighlighting: PsiElement
    // An element that should be navigated to if the name should be navigated to
    def forNavigation: PsiElement = forHighlighting
    // Actually a text name
    def explicitIdentifier: Option[PsiElement]

    // Either a text name or something that stands in its stead but behaves like a name (like _ or ?)
    def place: Option[PsiElement]

    def isElement(element: PsiElement): Boolean

    // The same as `place`, but if no place exists, it is created
    def prepareToReplace(): PsiElement
  }

  object NameId {
    trait NonAnonymous extends NameId {
      override def isAnonymous: false = false
      override def name: Some[String]
    }

    trait Anonymous extends NameId {
      override def isAnonymous: true = true
      override def name: None.type = None
      override def explicitName: None.type = None
      override def explicitIdentifier: None.type = None
      override def forcedName: String = AnonymousPlaceholder
    }

    abstract class Placed extends NameId {
      def placeElement: PsiElement
      override def name: Option[String] = explicitName
      override def explicitName: Option[String] =
        explicitIdentifier.map(_.getText)
      override def forHighlighting: PsiElement = placeElement
      override def place: Some[PsiElement] = Some(placeElement)
      override def isElement(element: PsiElement): Boolean = placeElement == element
      override def prepareToReplace(): PsiElement = placeElement

      def startOffset: Int = placeElement.getTextOffset
      def endOffset: Int = startOffset + placeElement.getTextLength
      def textRange: TextRange = placeElement.getTextRange
    }

    final class Name(val nameElement: PsiElement) extends Placed with NonAnonymous {
      assert(nameElement != null)
      override def placeElement: PsiElement = nameElement

      override def name: Some[String] = explicitName
      override def explicitName: Some[String] = Some(nameElement.getText)
      override def explicitIdentifier: Some[PsiElement] = Some(nameElement)
      override def forcedName: String = nameElement.getText
    }

    class Placeholder(val placeholderElement: PsiElement) extends Placed {
      assert(placeholderElement != null)

      override def placeElement: PsiElement = placeholderElement
      override def isAnonymous: true = true
      override def forcedName: String = AnonymousPlaceholder
      override def explicitIdentifier: Option[PsiElement] = None
    }

    class Error(val errorElement: PsiElement) extends Placed {
      assert(errorElement != null)

      override def isAnonymous: true = true
      override def placeElement: PsiElement = errorElement
      override def forcedName: String = MissingNamePlaceholder
      override def explicitIdentifier: Option[PsiElement] = None
    }

    abstract class Immaterial extends NameId {
      override def explicitName: None.type = None
      override def explicitIdentifier: None.type = None
      override def place: None.type = None
      override def isElement(element: PsiElement): Boolean = false
    }

    abstract class Synthetic extends NameId {
      override def explicitIdentifier: None.type = None
      override def isElement(element: PsiElement): Boolean = false
      override def forHighlighting: PsiElement = throw new UnsupportedOperationException("Cannot highlight stubbed/synthetic name")
      override def forNavigation: PsiElement = throw new UnsupportedOperationException("Cannot navigate to stubbed/synthetic name")
      override def place: None.type = None
      override def prepareToReplace(): PsiElement = throw new UnsupportedOperationException("Cannot replace stubbed/synthetic name")
    }

    class SyntheticName(override val forcedName: String) extends Synthetic with NonAnonymous {
      assert(forcedName != null)

      override def name: Some[String] = Some(forcedName)
      override def explicitName: Some[String] = Some(forcedName)
    }

    class NoName(override val forHighlighting: PsiElement) extends Immaterial with Anonymous {
      assert(forHighlighting != null)
      override def prepareToReplace(): PsiElement = throw new UnsupportedOperationException("Element cannot have a name")
    }

    // For tokens gathered with TokenSets.ID_SET
    def fromIdSetToken(token: PsiElement): NameId.Placed = {
      token.elementType match {
        case ScalaTokenTypes.tIDENTIFIER => new Name(token)
        case ScalaTokenTypes.tUNDER => new Placeholder(token)
      }
    }
  }
}

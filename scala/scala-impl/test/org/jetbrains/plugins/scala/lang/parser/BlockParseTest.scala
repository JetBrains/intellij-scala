package org.jetbrains.plugins.scala.lang.parser

import com.intellij.lang.PsiBuilderFactory
import com.intellij.psi.impl.source.DummyHolderFactory
import com.intellij.psi.impl.source.tree.{FileElement, TreeElement}
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilderImpl
import org.jetbrains.plugins.scala.lang.parser.parsing.expressions.BlockExpr
import org.junit.Assert

class BlockParseTest extends SimpleTestCase {
  def parseBlock(s: String): PsiElement = {
    val context = parseText("")
    val holder: FileElement = DummyHolderFactory.createHolder(context.getManager, context).getTreeElement
    val builder: ScalaPsiBuilderImpl = {
      val delegate = PsiBuilderFactory.getInstance.createBuilder(context.getProject, holder, new ScalaLexer(false, null), ScalaLanguage.INSTANCE, s)
      new ScalaPsiBuilderImpl(delegate, isScala3 = false)
    }
    BlockExpr.parse(builder)
    val node = builder.getTreeBuilt
    holder.rawAddChildren(node.asInstanceOf[TreeElement])
    node.getPsi
  }

  def doTest(s: String): Unit = {
    val elem = parseBlock(s)
    Assert.assertEquals(s, elem.getText)
  }

  def testBlock(): Unit = {
    doTest(
"""{
  val a = new df
  val agaga =  agadg+"/"+aa
  val agd = try {
    ag.agd.adgasdg(asgadg, false, ag)
  } catch {
    d:
  }
  val adg = asdgasdg(adgasdg.asdg(asdg))
  asdg.asdg.adsg(asdgasdg,-1)
  asdg
}"""
    )
  }

  def testBlock2(): Unit = {
    doTest(
"""{
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
    val entityType = ref.getParent match {
      case call: ScMethodCall => call.expectedType.map(_.presentableText)
      case _ => ref.expectedType.map(_.presentableText)
    }
    val parameters:
    inWriteAction {
      val place = if (ref.qualifier.isDefined) anchorForQualified(ref) else anchorForUnqualified(ref)
      for (anchor <- place; holder <- anchor.parent) {
        val placeholder = if (entityType.isDefined) "%s %s%s: Int" else "%s %s%s"
        val text = placeholder.format(keyword, ref.nameId.getText, parameters.mkString)
        val entity = holder.addAfter(parseElement(text, ref.getManager), anchor)
        if (ref.qualifier.isEmpty)
          holder.addBefore(createNewLine(ref.getManager, "\n\n"), entity)
        val builder = new TemplateBuilderImpl(entity)
        for (aType <- entityType;
             typeElement <- entity.children.findByType(classOf[ScSimpleTypeElement])) {
          builder.replaceElement(typeElement, aType)
        }
        entity.depthFirst.filterByType(classOf[ScParameter]).foreach { parameter =>
          val id = parameter.getNameIdentifier
          builder.replaceElement(id, id.getText)
          parameter.paramType.foreach { it =>
            builder.replaceElement(it, it.getText)
          }
        }
        CodeInsightUtilBase.forcePsiPostprocessAndRestoreElement(entity)
        val template = builder.buildTemplate()
        val targetFile = entity.getContainingFile
        val newEditor = positionCursor(project, targetFile, entity.getLastChild)
        val range = entity.getTextRange
        newEditor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
        TemplateManager.getInstance(project).startTemplate(newEditor, template)
      }
    }
}"""
    )


  }

  def testBlock3(): Unit = {
      doTest(
"""{
      var asdga = adf
      val adf = """"" + """"
        gads P { fasdf A, B; }
      """"" + """"
      val a = adga(adfad, 'A)
      val b = adga(fadsfa, 'B)

      case class ->(asdfad: Symbol, adfasd: Any)
      implicit def adsfasdf(adfad: ->):
      adsgadsf {
        actor { adsgasdfa(a,b) }
        actor {
          adfada.adsfad { sadsfasdf =>
            sdasfasd ! 'B -> ('label, 42, "foo")
          }
        }
        bdfasdf.asdfas { sasdfasdf =>
          sasdfasdfa.receive('A,'B) {
            case ('A, ('label, i, str)) => asdfasdf = true
            case 'B -> (i:Int) =>
          }
        }
      }

      assert(adsfadsf, "")
    }"""
      )
    }

    def testBlock4(): Unit = {
      doTest(
"""{
    val asdfadf = fadfad.:
    fadfa {
      dafsdfa {
        case 'take => asdfasd(asdfadf)
      }
    }
  }"""
      )
    }
}
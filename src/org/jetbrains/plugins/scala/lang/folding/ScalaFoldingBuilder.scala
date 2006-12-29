import scala.collection.mutable._

package org.jetbrains.plugins.scala.lang.folding{

  import java.util.ArrayList;
  import com.intellij.lang.ASTNode;
  import com.intellij.lang.folding.FoldingBuilder;
  import com.intellij.lang.folding.FoldingDescriptor;
  import com.intellij.openapi.editor.Document;
  import com.intellij.psi.tree.IElementType;
  import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes;
  import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes;

  /*
  * @author Ilya Sergey
  *
  */

  class ScalaFoldingBuilder extends FoldingBuilder {
    /*
    public FoldingDescriptor[] buildFoldRegions(@NotNull ASTNode astNode, @NotNull Document document) {
        List<FoldingDescriptor> descriptors = new ArrayList<FoldingDescriptor>();
        appendDescriptors(astNode, descriptors);
        return descriptors.toArray(new FoldingDescriptor[descriptors.size()]);
    }


    @Nullable
    public String getPlaceholderText(@NotNull ASTNode node) {
        final IElementType type = node.getElementType();

        if (type == RubyElementTypes.MODULE) {
            return MODULE_FOLD_TEXT;
        }
        if (type == RubyElementTypes.CLASS) {
            return CLASS_FOLD_TEXT;
        }
        if (type == RubyElementTypes.SINGLETON) {
            return SINGLETON_FOLD_TEXT;
        }
        if (type == RubyElementTypes.METHOD) {
            return METHOD_FOLD_TEXT;
        }
        if (type == RubyElementTypes.SINGLETON_METHOD){
            return SINGLETON_METHOD_FOLD_TEXT;
        }
        if (type == RubyElementTypes.HEREDOC_VALUE) {
            return HEREDOC_FOLD_TEXT;
        }

        if (type == RubyElementTypes.CODE_BLOCK) {
            if (((RCodeBlock) node.getPsi()).getType()== RCodeBlock.BLOCK_TYPE.BRACE_BLOCK)  {
                return CODE_BRACE_BLOCK_FOLD_TEXT;
            } else {
                return CODE_DO_BLOCK_FOLD_TEXT;
            }
        }

        if (type == RubyTokenTypes.tEND_MARKER){
            return END_MARKER_FOLD_TEXT;
        }
        return null;
    }

    public boolean isCollapsedByDefault(@NotNull ASTNode node) {
        if (node.getElementType() == RubyTokenTypes.tEND_MARKER){
            return true;
        }
        return false;
    }
    */

    private def appendDescriptors (node: ASTNode,
                                   document: Document,
                                   descriptors: ListBuffer[FoldingDescriptor]): Unit = {
      if (node.getElementType() == ScalaElementTypes.BLOCK_EXPR) {
         descriptors += (new FoldingDescriptor(node, node.getTextRange()))
      }

      var child = node.getFirstChildNode()
      while (child != null) {
         appendDescriptors(child, document, descriptors)
         child = child.getTreeNext()
      }
    }

    def buildFoldRegions(astNode: ASTNode, document: Document) : Array[FoldingDescriptor] = {
      var descriptors = new ListBuffer[FoldingDescriptor]
      appendDescriptors(astNode, document, descriptors);
      var list = descriptors.toList
      var dArray = new Array[FoldingDescriptor](descriptors.length)
      var i = 0
      while (!list.isEmpty) {
        dArray(i) = list.head
        list = list.drop(1)
        i=i+1
      }
      dArray
    }

    def getPlaceholderText(node : ASTNode): String = {
      if (node.getElementType() == ScalaElementTypes.BLOCK_EXPR) {
         return "{...}";
      }
      null
    }

    def isCollapsedByDefault(node: ASTNode): Boolean = {false}

  }
}
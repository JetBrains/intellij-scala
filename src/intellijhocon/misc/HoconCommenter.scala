package intellijhocon
package misc

import com.intellij.lang.Commenter

class HoconCommenter extends Commenter {
  def getLineCommentPrefix = "//"

  def getBlockCommentSuffix = null

  def getBlockCommentPrefix = null

  def getCommentedBlockCommentPrefix = null

  def getCommentedBlockCommentSuffix = null
}

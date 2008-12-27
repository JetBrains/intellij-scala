implement removeAll
class ExtendsList extends java.util.List {
  <caret>
}<end>
import java.util.Collection

class ExtendsList extends java.util.List {
  def removeAll(c: Collection[_]): Boolean = false
}
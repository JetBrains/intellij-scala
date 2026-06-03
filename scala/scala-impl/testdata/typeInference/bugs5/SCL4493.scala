package code.snippet {
package object campaign {
  case class Entry(priorityName: String)
}
}

package other {
import code.snippet.campaign. Entry

class Z {
  /*start*/Entry("text")/*end*/
}
}
//campaign.Entry
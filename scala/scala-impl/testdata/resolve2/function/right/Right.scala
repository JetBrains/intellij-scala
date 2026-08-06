object O {
  def ::(p: Int) = {}
  def :::(p: Int) = {}
  def +:(p: Int) = {}
}

O./* line: 2 */::(1)
1/* line: 2 */::O

O./* line: 3 */:::(1)
1/* line: 3 */:::O

O./* line: 4 */+:(1)
1/* line: 4 */+:O


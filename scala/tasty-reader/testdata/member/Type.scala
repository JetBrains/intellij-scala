package member

trait Type {
  type Abstract

  type Alias = Int

  opaque type Opaque/**/ = String/**/

  opaque type OpaqueLowerBound >: String/**/ = Any/**/

  opaque type OpaqueUpperBound <: String/**/ = Nothing/**/

  opaque type OpaqueLowerAndUpperBounds >: String <: AnyRef/**/ = CharSequence/**/

  opaque type OpaqueReifiable/**/ = Int/**/

  opaque type OpaqueReifiableLowerBound >: Int/**/ = Int/**/

  opaque type OpaqueReifiableUpperBound <: AnyVal/**/ = Int/**/

  opaque type OpaqueReifiableLowerAndUpperBounds >: Int <: AnyVal/**/ = Int/**/
}
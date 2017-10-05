object test {
  case class Device(name: String)

  type ProfileMappings = PartialFunction[Device, String]
  private var _profileMappings: ProfileMappings = _

  def profileMappings = _profileMappings

  def profileMappings_=(mappings: ProfileMappings) {}

  profileMappings = {
    case device if device./*line: 2*/name.endsWith("PFH") => "profile_A"
  }: ProfileMappings

  profileMappings = {
    case device if device./*line: 2*/name.endsWith("PFH") => "profile_A"
  }
}
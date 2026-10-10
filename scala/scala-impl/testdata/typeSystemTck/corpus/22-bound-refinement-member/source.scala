// Concept: member-type resolution through a REFINEMENT placed on the BOUND of an
// abstract type member, where the refined member (`type T`) is inherited
// TRANSITIVELY by the refined component (SCL-21947, the scalac `MutableSettings`
// shape).
//
//   type BooleanSetting  <: Setting {type T = Boolean}
//   type BooleanSetting1 <: Setting with SettingValue {type T = Boolean}
//
// `Setting <: SettingValue` and `def value: T` lives in `SettingValue`, where `T`
// is the abstract member inherited from `AbsSettingValue`. Selecting `.value` on a
// `BooleanSetting` must read the refinement `{type T = Boolean}` and report the
// result as `Boolean`. The two forms differ only in whether `SettingValue` is a
// DIRECT component of the compound (`BooleanSetting1`) or reached via `Setting`'s
// upper bound (`BooleanSetting`). scalac answers `Boolean` for both; the term
// probes below capture that ground truth.
abstract class AbsSettings {
  class AbsSettingValue {
    type T
  }
  trait SettingValue extends AbsSettingValue {
    def value: T
  }
}
abstract class MutableSettings extends AbsSettings {
  type Setting <: SettingValue
  type BooleanSetting  <: Setting {type T = Boolean}
  type BooleanSetting1 <: Setting with SettingValue {type T = Boolean}
  /*ANCHOR inSettings*/
}

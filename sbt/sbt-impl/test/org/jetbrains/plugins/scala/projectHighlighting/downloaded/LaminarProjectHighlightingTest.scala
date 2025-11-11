package org.jetbrains.plugins.scala.projectHighlighting.downloaded

import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.projectHighlighting.base.GithubRepositoryWithRevision

class LaminarProjectHighlightingTest extends GithubSbtAllProjectHighlightingTest {

  //version v15.0.0-M1
  override protected def githubRepositoryWithRevision: GithubRepositoryWithRevision =
    GithubRepositoryWithRevision("raquo", "laminar", "v15.0.0-M1")

  import org.jetbrains.plugins.scala.util.TextRangeUtils.ImplicitConversions.tupleToTextRange

  override protected def filesWithProblems: Map[String, Set[TextRange]] = Map(
    "project/DomDefsGenerator.scala" -> Set(
      (1135, 1146), // Cannot resolve symbol metaProject
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/AlignContent.scala" -> Set(
      (534, 549), // Type mismatch, expected: _$1 | String, actual: Any | String
      (597, 611), // Type mismatch, expected: _$1 | String, actual: Any | String
      (659, 673), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Auto.scala" -> Set(
      (497, 503), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/BackfaceVisibility.scala" -> Set(
      (549, 558), // Type mismatch, expected: _$1 | String, actual: Any | String
      (640, 648), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/BackgroundAttachment.scala" -> Set(
      (740, 747), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1142, 1149), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1365, 1373), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/BackgroundSize.scala" -> Set(
      (914, 921), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1215, 1224), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/BorderCollapse.scala" -> Set(
      (585, 595), // Type mismatch, expected: _$1 | String, actual: Any | String
      (693, 703), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/BoxSizing.scala" -> Set(
      (507, 519), // Type mismatch, expected: _$1 | String, actual: Any | String
      (566, 579), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Clear.scala" -> Set(
      (573, 579), // Type mismatch, expected: _$1 | String, actual: Any | String
      (684, 691), // Type mismatch, expected: _$1 | String, actual: Any | String
      (809, 815), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Color.scala" -> Set(
      (499, 506), // Type mismatch, expected: _$1 | String, actual: Any | String
      (547, 553), // Type mismatch, expected: _$1 | String, actual: Any | String
      (594, 600), // Type mismatch, expected: _$1 | String, actual: Any | String
      (641, 647), // Type mismatch, expected: _$1 | String, actual: Any | String
      (689, 696), // Type mismatch, expected: _$1 | String, actual: Any | String
      (739, 747), // Type mismatch, expected: _$1 | String, actual: Any | String
      (790, 798), // Type mismatch, expected: _$1 | String, actual: Any | String
      (838, 843), // Type mismatch, expected: _$1 | String, actual: Any | String
      (885, 892), // Type mismatch, expected: _$1 | String, actual: Any | String
      (935, 943), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Cursor.scala" -> Set(
      (570, 579), // Type mismatch, expected: _$1 | String, actual: Any | String
      (682, 696), // Type mismatch, expected: _$1 | String, actual: Any | String
      (776, 782), // Type mismatch, expected: _$1 | String, actual: Any | String
      (889, 898), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1076, 1086), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1196, 1202), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1291, 1297), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1412, 1423), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1527, 1533), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1666, 1681), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1782, 1789), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1879, 1885), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1968, 1974), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2093, 2102), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2204, 2217), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2345, 2357), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2550, 2562), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2758, 2770), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2852, 2862), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2946, 2956), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3041, 3051), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3134, 3144), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3235, 3246), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3336, 3347), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3441, 3452), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3545, 3556), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3652, 3663), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3759, 3770), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3881, 3894), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4005, 4018), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4125, 4134), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4243, 4253), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4366, 4372), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4489, 4499), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Direction.scala" -> Set(
      (557, 562), // Type mismatch, expected: _$1 | String, actual: Any | String
      (658, 663), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Display.scala" -> Set(
      (593, 600), // Type mismatch, expected: _$1 | String, actual: Any | String
      (707, 715), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1457, 1463), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1664, 1675), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1838, 1844), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2004, 2010), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2246, 2252), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2474, 2488), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2659, 2672), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2840, 2853), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3132, 3146), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3566, 3572), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3762, 3772), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3951, 3962), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4098, 4105), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4201, 4216), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4304, 4316), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4436, 4450), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4580, 4600), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4727, 4747), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4874, 4894), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4981, 4992), // Type mismatch, expected: _$1 | String, actual: Any | String
      (5116, 5133), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/EmptyCells.scala" -> Set(
      (576, 582), // Type mismatch, expected: _$1 | String, actual: Any | String
      (689, 695), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/FlexDirection.scala" -> Set(
      (701, 709), // Type mismatch, expected: _$1 | String, actual: Any | String
      (841, 857), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1076, 1081), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1214, 1227), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/FlexPosition.scala" -> Set(
      (525, 537), // Type mismatch, expected: _$1 | String, actual: Any | String
      (581, 591), // Type mismatch, expected: _$1 | String, actual: Any | String
      (634, 642), // Type mismatch, expected: _$1 | String, actual: Any | String
      (684, 691), // Type mismatch, expected: _$1 | String, actual: Any | String
      (731, 736), // Type mismatch, expected: _$1 | String, actual: Any | String
      (782, 794), // Type mismatch, expected: _$1 | String, actual: Any | String
      (838, 848), // Type mismatch, expected: _$1 | String, actual: Any | String
      (893, 903), // Type mismatch, expected: _$1 | String, actual: Any | String
      (953, 969), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1018, 1033), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1077, 1086), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/FlexWrap.scala" -> Set(
      (715, 723), // Type mismatch, expected: _$1 | String, actual: Any | String
      (994, 1000), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1142, 1156), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Float.scala" -> Set(
      (581, 587), // Type mismatch, expected: _$1 | String, actual: Any | String
      (700, 707), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/FontSize.scala" -> Set(
      (641, 651), // Type mismatch, expected: _$1 | String, actual: Any | String
      (694, 703), // Type mismatch, expected: _$1 | String, actual: Any | String
      (745, 752), // Type mismatch, expected: _$1 | String, actual: Any | String
      (795, 803), // Type mismatch, expected: _$1 | String, actual: Any | String
      (845, 852), // Type mismatch, expected: _$1 | String, actual: Any | String
      (895, 904), // Type mismatch, expected: _$1 | String, actual: Any | String
      (948, 958), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1142, 1150), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1336, 1345), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/FontStyle.scala" -> Set(
      (629, 637), // Type mismatch, expected: _$1 | String, actual: Any | String
      (729, 738), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/FontWeight.scala" -> Set(
      (571, 579), // Type mismatch, expected: _$1 | String, actual: Any | String
      (660, 666), // Type mismatch, expected: _$1 | String, actual: Any | String
      (826, 835), // Type mismatch, expected: _$1 | String, actual: Any | String
      (992, 1000), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/GlobalKeywords.scala" -> Set(
      (627, 636), // Type mismatch, expected: _$1 | String, actual: Any | String
      (842, 851), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1135, 1143), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1587, 1594), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/JustifyContent.scala" -> Set(
      (528, 534), // Type mismatch, expected: _$1 | String, actual: Any | String
      (576, 583), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Line.scala" -> Set(
      (727, 735), // Type mismatch, expected: _$1 | String, actual: Any | String
      (986, 994), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1086, 1093), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1270, 1278), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1407, 1415), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1586, 1593), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1852, 1859), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2122, 2130), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/LineWidth.scala" -> Set(
      (696, 702), // Type mismatch, expected: _$1 | String, actual: Any | String
      (802, 810), // Type mismatch, expected: _$1 | String, actual: Any | String
      (909, 916), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/ListStylePosition.scala" -> Set(
      (573, 582), // Type mismatch, expected: _$1 | String, actual: Any | String
      (758, 766), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/ListStyleType.scala" -> Set(
      (560, 566), // Type mismatch, expected: _$1 | String, actual: Any | String
      (634, 642), // Type mismatch, expected: _$1 | String, actual: Any | String
      (710, 718), // Type mismatch, expected: _$1 | String, actual: Any | String
      (803, 812), // Type mismatch, expected: _$1 | String, actual: Any | String
      (888, 901), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1005, 1027), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1108, 1121), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1202, 1215), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1297, 1310), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1390, 1403), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1483, 1496), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1576, 1589), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1669, 1682), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1767, 1777), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1862, 1872), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1953, 1961), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2033, 2043), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2169, 2185), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2257, 2267), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2393, 2409), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/MinMaxLength.scala" -> Set(
      (689, 702), // Type mismatch, expected: _$1 | String, actual: Any | String
      (788, 801), // Type mismatch, expected: _$1 | String, actual: Any | String
      (920, 933), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1053, 1069), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/MixBlendMode.scala" -> Set(
      (524, 534), // Type mismatch, expected: _$1 | String, actual: Any | String
      (577, 585), // Type mismatch, expected: _$1 | String, actual: Any | String
      (629, 638), // Type mismatch, expected: _$1 | String, actual: Any | String
      (681, 689), // Type mismatch, expected: _$1 | String, actual: Any | String
      (733, 742), // Type mismatch, expected: _$1 | String, actual: Any | String
      (789, 802), // Type mismatch, expected: _$1 | String, actual: Any | String
      (848, 860), // Type mismatch, expected: _$1 | String, actual: Any | String
      (906, 918), // Type mismatch, expected: _$1 | String, actual: Any | String
      (964, 976), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1023, 1035), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1081, 1092), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1132, 1137), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1184, 1196), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1238, 1245), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1292, 1304), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/None.scala" -> Set(
      (497, 503), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Normal.scala" -> Set(
      (501, 509), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Overflow.scala" -> Set(
      (625, 634), // Type mismatch, expected: _$1 | String, actual: Any | String
      (741, 749), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1061, 1069), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/OverflowWrap.scala" -> Set(
      (605, 613), // Type mismatch, expected: _$1 | String, actual: Any | String
      (823, 835), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/PaddingBoxSizing.scala" -> Set(
      (533, 546), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/PageBreak.scala" -> Set(
      (552, 560), // Type mismatch, expected: _$1 | String, actual: Any | String
      (630, 637), // Type mismatch, expected: _$1 | String, actual: Any | String
      (756, 762), // Type mismatch, expected: _$1 | String, actual: Any | String
      (883, 890), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/PointerEvents.scala" -> Set(
      (729, 735), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1180, 1186), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1667, 1683), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2018, 2031), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2373, 2388), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2749, 2758), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3252, 3261), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3543, 3549), // Type mismatch, expected: _$1 | String, actual: Any | String
      (3838, 3846), // Type mismatch, expected: _$1 | String, actual: Any | String
      (4173, 4178), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Position.scala" -> Set(
      (707, 715), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1173, 1183), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1509, 1519), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1804, 1811), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/TableLayout.scala" -> Set(
      (714, 720), // Type mismatch, expected: _$1 | String, actual: Any | String
      (962, 969), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/TextAlign.scala" -> Set(
      (572, 579), // Type mismatch, expected: _$1 | String, actual: Any | String
      (688, 693), // Type mismatch, expected: _$1 | String, actual: Any | String
      (809, 815), // Type mismatch, expected: _$1 | String, actual: Any | String
      (933, 940), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1046, 1054), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1247, 1256), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/TextDecoration.scala" -> Set(
      (567, 578), // Type mismatch, expected: _$1 | String, actual: Any | String
      (671, 681), // Type mismatch, expected: _$1 | String, actual: Any | String
      (787, 801), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/TextOverflow.scala" -> Set(
      (838, 844), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1200, 1210), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/TextTransform.scala" -> Set(
      (649, 661), // Type mismatch, expected: _$1 | String, actual: Any | String
      (768, 779), // Type mismatch, expected: _$1 | String, actual: Any | String
      (886, 897), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/TextUnderlinePosition.scala" -> Set(
      (653, 659), // Type mismatch, expected: _$1 | String, actual: Any | String
      (974, 981), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1208, 1214), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1443, 1450), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1496, 1508), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1555, 1568), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/VerticalAlign.scala" -> Set(
      (938, 948), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1091, 1096), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1245, 1252), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1378, 1388), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1539, 1552), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1705, 1713), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1861, 1866), // Type mismatch, expected: _$1 | String, actual: Any | String
      (2023, 2031), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/Visibility.scala" -> Set(
      (549, 558), // Type mismatch, expected: _$1 | String, actual: Any | String
      (794, 802), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1076, 1086), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/WhiteSpace.scala" -> Set(
      (711, 719), // Type mismatch, expected: _$1 | String, actual: Any | String
      (879, 887), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1069, 1074), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1266, 1276), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1468, 1478), // Type mismatch, expected: _$1 | String, actual: Any | String
    ),
    "src/main/scala/com/raquo/laminar/defs/styles/traits/WordBreak.scala" -> Set(
      (667, 678), // Type mismatch, expected: _$1 | String, actual: Any | String
      (867, 877), // Type mismatch, expected: _$1 | String, actual: Any | String
      (1149, 1161), // Type mismatch, expected: _$1 | String, actual: Any | String
    )
  )
}

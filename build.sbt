name := "ScalaExamplePlugin"
version := "0.1"
scalaVersion := "2.13.3"

resolvers += "paper-mc" at "https://papermc.io/repo/repository/maven-public/"
resolvers += "aikar-repo" at "https://repo.aikar.co/content/groups/aikar/"

libraryDependencies += "co.aikar" % "acf-paper" % "0.5.0-SNAPSHOT"
libraryDependencies += "com.destroystokyo.paper" % "paper-api" % "1.16.1-R0.1-SNAPSHOT" % "provided"
libraryDependencies += "org.scala-lang" % "scala-library" % "2.13.3"

//logLevel in assembly := Level.Debug

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("scala.**" -> "me.jantuck.scalaexampleplugin.shaded.scalalib.@1")
    .inLibrary("org.scala-lang" % "scala-library" % "2.13.3")
    .inProject,
  ShadeRule.rename("co.aikar.commands.**" -> "me.jantuck.scalaexampleplugin.shaded.acf.@1")
  .inLibrary("co.aikar" % "acf-paper" % "0.5.0-SNAPSHOT")
  .inProject,
  ShadeRule.rename("co.aikar.locales.**" -> "me.jantuck.scalaexampleplugin.shaded.locales.@1")
    .inLibrary("co.aikar" % "acf-paper" % "0.5.0-SNAPSHOT")
    .inProject
)

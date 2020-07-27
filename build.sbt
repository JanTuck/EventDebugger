name := "EventDebugger"
version := "0.1"
scalaVersion := "2.13.3"

resolvers += "paper-mc" at "https://papermc.io/repo/repository/maven-public/"

libraryDependencies += "com.destroystokyo.paper" % "paper-api" % "1.16.1-R0.1-SNAPSHOT" % "provided"
libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value
libraryDependencies += "com.esotericsoftware" % "reflectasm" % "1.11.9"

//logLevel in assembly := Level.Debug

assemblyShadeRules in assembly := Seq(
  ShadeRule.rename("scala.**" -> "me.jantuck.eventdebugger.shaded.scalalib.@1")
    .inAll
    .inProject,
  ShadeRule.rename("com.esotericsoftware.**" -> "me.jantuck.eventdebugger.shaded.@1")
    .inAll
    .inProject,
)

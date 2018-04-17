name := "twitterServise"
 
version := "1.0" 
      
lazy val `twitterservise` = (project in file(".")).enablePlugins(PlayScala)

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
      
resolvers += "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
      
scalaVersion := "2.12.4"

libraryDependencies ++= Seq(
  jdbc , ehcache , ws , specs2 % Test , guice,
  "com.typesafe.play" %% "play-iteratees" % "2.6.1",
  "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.13.0-play26")

//unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )

      
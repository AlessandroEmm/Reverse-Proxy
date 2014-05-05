import sbt._
import Keys._
import com.typesafe.sbt.osgi.SbtOsgi._
import com.typesafe.sbt.osgi.OsgiKeys._
import com.typesafe.sbt.SbtScalariform._
import com.typesafe.sbteclipse.plugin.EclipsePlugin._

object Values {


  val group = Seq("ch","ale")
  
  val groupId = group.mkString(".")
    
  def artifactId(name: String*) = (group ++ name).mkString("-")
  
  def bundleSymbolicName(name: String*) = (group ++ name).mkString(".")

  def osgiVersion(v: String) = { 
    val V = "^(.*?)(?:-SNAPSHOT)?$".r
    v match {
       case V(vers) => s"$vers.${System.currentTimeMillis}"
    }
  }
  
}

object BuildSettings {

    lazy val buildSettings = Defaults.defaultSettings ++ /*unidocSettings ++*/ scalariformSettings ++ org.scalastyle.sbt.ScalastylePlugin.Settings ++ Seq(
      EclipseKeys.withSource := true,

      organization := Values.groupId,
      organizationName := "Reverse Proxy",
      organizationHomepage := Some(url("http://github.com/AlessandroEmm/Reverse-Proxy")),

      scalaVersion := "2.11.0",
      scalacOptions ++= Seq("-feature","-unchecked","-deprecation","–encoding","UTF8","–explaintypes","-Xfuture"/*,"-Xlint"*/),
      scalacOptions in (Compile,doc) := Seq("-groups", "-implicits","-diagrams","-deprecation"),

      libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.2",
      libraryDependencies += "io.spray" %% "spray-routing" % "1.3.1-20140423",
      libraryDependencies += "io.spray" %% "spray-can" % "1.3.1-20140423",
      libraryDependencies += "io.spray" %% "spray-client" % "1.3.1-20140423",
      libraryDependencies +=  "org.scala-lang.modules" %% "scala-xml" % "1.0.1",     
      libraryDependencies += "org.jsoup" % "jsoup" % "1.7.3"
       
     
)
}

object FrameworkCoreBuild extends Build {



  import BuildSettings._

    lazy val default = Project(
      id = "default", base = file("."), 
      settings = buildSettings ++ Seq(
	        privatePackage := Seq()
	        ,publishLocal := {}
	        ,publish := {} 

      )
    ).aggregate(proxy)


    lazy val proxy = Project(
    	id = "proxy",
        base = file("proxy"),

        settings =  buildSettings ++ osgiSettings ++ Seq(
            name := Values.bundleSymbolicName("reverseproxy"),
                bundleSymbolicName := Values.bundleSymbolicName(),
        exportPackage := Seq("ch.ale.reverseproxy")
        )
    ) 
}

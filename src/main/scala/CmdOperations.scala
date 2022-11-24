import java.io.File
import scala.language.postfixOps
import scala.sys.process.Process

object CmdOperations {

  def runCmdWaiting(cmdWindows: Option[String], cmdUnix: Option[String], path: File): String = {
    import scala.sys.process._
    val isWindows: Boolean = System.getProperty("os.name").toLowerCase().contains("win")
    try {
      if (isWindows && cmdWindows.nonEmpty) {
        Process(cmdWindows.get, path) !!
      } else {
        if cmdUnix.nonEmpty then
          Process("bash -c " + cmdUnix.get, path) !!
        else throw Exception("No cmd is provided")
      }
    } catch {
      case ex: Throwable =>
        println(s"Error: Could not execute command ${
          if (isWindows && cmdWindows.nonEmpty) cmdWindows.get
          else if cmdUnix.nonEmpty then cmdUnix.get else ""
        } in path ${path.getAbsolutePath} directory\n [exception]: " + ex.getMessage)
        throw ex
    }
  }

  def runCmdNoWait(cmdWindows: Option[String], cmdUnix: Option[String], path: File): Process =
    import scala.sys.process._
    val isWindows: Boolean = System.getProperty("os.name").toLowerCase().contains("win")
    try {
      if (isWindows && cmdWindows.nonEmpty) {
        Process("cmd /c " + path.getAbsolutePath + "\\" + cmdWindows.get, path).run()
      } else {
        if cmdUnix.nonEmpty then
          Process("bash -c " + "\"" + path.getAbsolutePath + "/" + cmdUnix.get + "\"", path).run()
        else throw Exception("No cmd is provided")
      }
    } catch {
      case ex: Throwable =>
        println(s"Error: Could not execute command ${
          if (isWindows && cmdWindows.nonEmpty) cmdWindows.get
          else if cmdUnix.nonEmpty then cmdUnix.get else ""
        } in path ${path.getAbsolutePath} directory\n [exception]: " + ex.getMessage)
        throw ex
    }
}

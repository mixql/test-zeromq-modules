import com.typesafe.config.*
import org.rogach.scallop.ScallopConf

import scala.sys.exit
import java.io.File

object MainServerApp {
  def main(args: Array[String]): Unit = {
    println("Server: Starting main")
    val (host, port, basePath) = parseArgs(args.toList)
    println("Server: host of server is " + host + " and port is " + port + " and base path is " +
      basePath.getAbsolutePath
    )
    val module3 = ClientModule("module-scala3", host, port, new File(basePath.getAbsolutePath))
    try {
      import app.zio.grpc.remote.clientMsgs.*
      module3.sendMsg(ZioMsgTest1("hello", "scala", "3")) match {
        case ZioMsgTestReply(msg, _) => println(s"Server: Got response from client module-scala-3: " + msg)
        case _: Any => throw Exception("Server: Got unknown  message client module-scala-3")
      }
      module3.sendMsg(ZioMsgTest2Array(Seq("hello", "scala", "3"))) match {
        case ZioMsgTestReply(msg, _) => println(s"Server: Got response from client module-scala-3: " + msg)
        case _: Any => throw Exception("Got unknown  message client module-scala-3")
      }

      module3.sendMsg(ZioMsgTest3Map(
        Map("msg1" -> "hello", "msg2" -> "scala", "msg3" -> "3")
      )) match {
        case ZioMsgTestReply(msg, _) => println(s"Server: Got response from client module-scala-3: " + msg)
        case _: Any => throw Exception("Got unknown  message client module-scala-3")
      }
      
      // Async.Await(module3.sendMsgAsync())
    } catch {
      case e: Throwable => println("Server: Got exception: "+ e.getMessage)
    } finally {
      module3.close()
    }
  }

  def parseArgs(args: List[String]): (String, Int, File) = {
    import org.rogach.scallop.ScallopConfBase
    val appArgs = AppArgs(args)
    val host: String = appArgs.host.toOption.get
    val port = PortOperations.isPortAvailable(
      appArgs.port.toOption.get
    )
    val basePath = appArgs.basePath.toOption.get
    (host, port, basePath)
  }
}

case class AppArgs(arguments: Seq[String]) extends ScallopConf(arguments) {

  import org.rogach.scallop.stringConverter
  import org.rogach.scallop.intConverter
  import org.rogach.scallop.fileConverter

  val port = opt[Int](required = false, default = Some(0))
  val host = opt[String](required = false, default = Some("0.0.0.0"))
  val basePath = opt[File](required = false, default = Some(new File(".")))
  verify()
}



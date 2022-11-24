import com.typesafe.config.*
import org.rogach.scallop.ScallopConf

import scala.sys.exit
import java.io.File

object MainServerApp {
  def main(args: Array[String]): Unit = {
    println("Server: Starting main")
    val (host, portFrontend, portBackend, basePath) = parseArgs(args.toList)
    println("Server: host of server is " + host + " and broker frontend port is " + portFrontend
      + " and backend port is " + portBackend + " and base path is " +
      basePath.getAbsolutePath
    )

    val module1 = ClientModule("client-scala3-1", "scala3-1", "module-scala3",
      host, portFrontend, portBackend, new File(basePath.getAbsolutePath))
    val module2 = ClientModule("client--scala3-2", "scala3-2", "module-scala3",
      host, portFrontend, portBackend, new File(basePath.getAbsolutePath))
    val module3 = ClientModule("client-scala3-3", "scala3-3", "module-scala3",
      host, portFrontend, portBackend, new File(basePath.getAbsolutePath))

    try {
      import app.zio.grpc.remote.clientMsgs.*
      module1.sendMsg(ZioMsgTest1("hello", "scala", "3"))
      println(s"Server: Got response from module scala-3-1: " +
        module1.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )
      module2.sendMsg(ZioMsgTest1("hello", "scala", "3"))
      println(s"Server: Got response from module scala-3-2: " +
        module2.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )
      module3.sendMsg(ZioMsgTest1("hello", "scala", "3"))
      println(s"Server: Got response from module scala-3-3: " +
        module3.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )

      module1.sendMsg(ZioMsgTest2Array(Seq("hello", "scala", "3")))
      println(s"Server: Got response from module scala-3-1: " +
        module1.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )
      module2.sendMsg(ZioMsgTest2Array(Seq("hello", "scala", "3")))
      println(s"Server: Got response from module scala-3-2: " +
        module2.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )
      module3.sendMsg(ZioMsgTest2Array(Seq("hello", "scala", "3")))
      println(s"Server: Got response from module scala-3-3: " +
        module3.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )

      module1.sendMsg(ZioMsgTest3Map(
        Map("msg1" -> "hello", "msg2" -> "scala", "msg3" -> "3"))
      )
      println(s"Server: Got response from module scala-3-1: " +
        module1.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )
      module2.sendMsg(ZioMsgTest3Map(
        Map("msg1" -> "hello", "msg2" -> "scala", "msg3" -> "3"))
      )
      println(s"Server: Got response from module scala-3-2: " +
        module2.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )
      module3.sendMsg(ZioMsgTest3Map(
        Map("msg1" -> "hello", "msg2" -> "scala", "msg3" -> "3"))
      )
      println(s"Server: Got response from module scala-3-3: " +
        module3.recvMsg().asInstanceOf[ZioMsgTestReply].msg
      )

      //Do we need to send shutdown. Need testing
      println(s"Server:Sending shutdown commands to modules: ")
      module1.sendMsg(ShutDown())
      module2.sendMsg(ShutDown())
      module3.sendMsg(ShutDown())
    } catch {
      case e: Throwable => println("Server: Got exception: " + e.getMessage)
    } finally {
      module1.close()
      module2.close()
      module3.close()
    }
  }

  def parseArgs(args: List[String]): (String, Int, Int, File) = {
    import org.rogach.scallop.ScallopConfBase
    val appArgs = AppArgs(args)
    val host: String = appArgs.host.toOption.get
    val portFrontend = PortOperations.isPortAvailable(
      appArgs.portFrontend.toOption.get
    )
    val portBackend = PortOperations.isPortAvailable(
      appArgs.portBackend.toOption.get
    )
    val basePath = appArgs.basePath.toOption.get
    (host, portFrontend, portBackend, basePath)
  }
}

case class AppArgs(arguments: Seq[String]) extends ScallopConf(arguments) {

  import org.rogach.scallop.stringConverter
  import org.rogach.scallop.intConverter
  import org.rogach.scallop.fileConverter

  val portFrontend = opt[Int](required = false, default = Some(0))
  val portBackend = opt[Int](required = false, default = Some(0))
  val host = opt[String](required = false, default = Some("0.0.0.0"))
  val basePath = opt[File](required = false, default = Some(new File(".")))
  verify()
}



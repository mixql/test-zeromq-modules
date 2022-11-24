import ClientModule.modulesNum

import java.io.File
import app.zio.grpc.remote.clientMsgs.*

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.{ServerSocketChannel, SocketChannel}
import org.zeromq.{SocketType, ZMQ}


object ClientModule {
  var modulesNum: Int = 0
  var broker: BrokerModule = null
}

class ClientModule(clientName: String, moduleName: String, startScriptName: String, host: String, portFrontend: Int,
                   portBackend: Int, basePath: File) extends java.lang.AutoCloseable {
  var client: ZMQ.Socket = null
  var ctx: ZMQ.Context = null

  var clientRemoteProcess: sys.process.Process = null

  import ClientModule.*


  def sendMsg(msg: scalapb.GeneratedMessage): scalapb.GeneratedMessage = {
    if clientRemoteProcess == null then
      startModuleClient()
      ctx = ZMQ.context(1)
      client = ctx.socket(SocketType.REQ)
      //set id for client
      println("server: Clientmodule " + name + " waiting for init msg")
      SocketOperations.readMsgFromSocket(client) match {
        //To-do Should be ClientMsgStartedSuccesss
        case ZioMsgTestReply(msg, _) => println(s"server: Got init msg from client $name: " + msg)
        case _: Any => throw Exception("server: Got unknown init message from client " + name)
      }
    end if

    SocketOperations.writeMsgToSocket(client, msg)
    SocketOperations.readMsgFromSocket(client)
  }

  def recvMsg() = {

  }

  def startModuleClient() = {
    if broker == null then
      modulesNum = modulesNum + 1
      broker = new BrokerModule(portFrontend, portBackend, host)
      println("Server: ClientModule: Starting broker messager")
      broker.start()
    println(s"server: trying to  start module $moduleName at " + host + " and port at " + portFrontend +
      " in " + basePath.getAbsolutePath
    )
    clientRemoteProcess = CmdOperations.runCmdNoWait(
      Some(s"$startScriptName.bat --port $portFrontend --host $host"),
      Some(s"$startScriptName --port $portFrontend --host $host"), basePath)
  }

  override def close() = {
    println("Server: Executing close")
    modulesNum = modulesNum - 1
    if (modulesNum <= 0) {
      println("Server: stop brocker")
      if broker != null then
        broker.close()
    }
    if (client != null) {
      println("Server: close client socket")
      client.close()
    }
    if (ctx != null) {
      println("Server: close context")
      ctx.close()
    }

    if (clientRemoteProcess.isAlive()) clientRemoteProcess.exitValue()
    println("server: Remote client was shutdown")

  }
}

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


  def sendMsg(msg: scalapb.GeneratedMessage): Unit = {
    if clientRemoteProcess == null then
      startModuleClient()
      ctx = ZMQ.context(1)
      client = ctx.socket(SocketType.REQ)
      //set id for client
      client.setIdentity(clientName.getBytes)
      println("server: Clientmodule " + clientName + " connected to " +
        s"tcp://$host:$portFrontend " + client.connect(s"tcp://$host:$portFrontend"))
    end if
    println("server: Clientmodule " + clientName + " sending identity of remote module " + moduleName + " " +
      client.send(moduleName.getBytes, ZMQ.SNDMORE))
    println("server: Clientmodule " + clientName + " sending empty frame to remote module " + moduleName + " " +
      client.send("".getBytes, ZMQ.SNDMORE))
    println("server: Clientmodule " + clientName + " sending protobuf message to remote module " + moduleName + " " +
      client.send(ProtoBufConverter.toArray(msg), 0))
  }

  def recvMsg(): scalapb.GeneratedMessage = {
    ProtoBufConverter.toProtobuf(client.recv(0))
  }

  def startModuleClient() = {
    if broker == null then
      broker = new BrokerModule(portFrontend, portBackend, host)
      println(s"Server: ClientModule $clientName: Starting broker messager")
      broker.start()
    println(s"server: ClientModule: $clientName trying to  start module $moduleName at " + host +
      " and port at " + portBackend + " in " + basePath.getAbsolutePath
    )
    modulesNum = modulesNum + 1
    clientRemoteProcess = CmdOperations.runCmdNoWait(
      Some(s"$startScriptName.bat --port $portBackend --host $host --identity $moduleName"),
      Some(s"$startScriptName --port $portBackend --host $host --identity $moduleName"), basePath)
  }

  override def close() = {
    println(s"Server: ClientModule: $clientName: Executing close")
    modulesNum = modulesNum - 1
    if (modulesNum <= 0) {
      println(s"Server: stop brocker")
      if broker != null then
        broker.close()
    }
    if (client != null) {
      println(s"Server: ClientModule: $clientName: close client socket")
      client.close()
    }
    if (ctx != null) {
      println(s"Server: ClientModule: $clientName: close context")
      ctx.close()
    }

//    if (clientRemoteProcess.isAlive()) clientRemoteProcess.exitValue()
//    println(s"server: ClientModule: $clientName: Remote client was shutdown")

  }
}

import com.typesafe.config.*
import org.rogach.scallop.ScallopConf

import java.net.InetSocketAddress
import java.net.SocketAddress
import java.nio.channels.SocketChannel
import java.text.MessageFormat
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Path
import java.nio.file.Paths
import app.zio.grpc.remote.clientMsgs.*
import org.zeromq.{SocketType, ZMQ}

import scala.annotation.tailrec

object ModuleScalaThree {
  var ctx: ZMQ.Context = null
  var server: ZMQ.Socket = null

  def main(args: Array[String]): Unit = {
    println("Client: Starting main client")
    val (host, port) = parseArgs(args.toList)
    println("Client: host of server is " + host + " and port is " + port.toString)

    try {
      ctx = ZMQ.context(1)
      server = ctx.socket(SocketType.REQ)
      println("Client: Connection established.")
      println("Client: Send msg to server that i am ready")
      server.send(ProtoBufConverter.toArray(Ready()))
      while (true) {
        val rs= readMsgFromServerBroker()
        val clientAdrress = rs._1
        val msg = rs._2
        ProtoBufConverter.toProtobuf(msg) match {
          case ZioMsgTest1(msg, msg2, msg3, _) =>
            println(s"Client: Received ZioMsgTest1 msg from server: ${msg} ${msg2} ${msg3}")
            println("Client: Sending reply on ZioMsgTest1 msg")
            sendMsgToServerBroker(clientAdrress, ZioMsgTestReply("Client module-scala3: " +
              "successfully received ZioMsgTest1"))
          case ZioMsgTest2Array(messages, _) =>
            println(s"Received ZioMsgTest2Array msg from server: ${messages.mkString(" ")}")
            println("Client: Sending reply on ZioMsgTest2Array msg")
            sendMsgToServerBroker(clientAdrress, ZioMsgTestReply("Client module-scala3: " +
              "successfully received ZioMsgTest2Array"))
          case ZioMsgTest3Map(msgMap, _) =>
            println(s"Received ZioMsgTest3Map msg from server: ${msgMap.mkString(" ")}")
            println("Client: Sending reply on ZioMsgTest3Map msg")
            sendMsgToServerBroker(clientAdrress, ZioMsgTestReply("Client module-scala3: " +
              "successfully received ZioMsgTest3Map"))
          case ShutDown(_) =>
            println("Started shutdown")
            throw BrakeException()
        }
      }
    } catch {
      case _: BrakeException =>
      case ex: Exception =>
        println("Client: Error: " + ex.getMessage)
    } finally {
      if server != null then
        server.close()
      if ctx != null then
        ctx.term()
    }
  }

  def parseArgs(args: List[String]): (String, Int) = {
    import org.rogach.scallop.ScallopConfBase
    val appArgs = AppArgs(args)
    val host: String = appArgs.host.toOption.get
    val port = //PortOperations.isPortAvailable(
      appArgs.port.toOption.get
    //)
    (host, port)
  }

  def sendMsgToServerBroker(clientAdrress: Array[Byte], msg: scalapb.GeneratedMessage) = {
    //Sending multipart message
    server.send(clientAdrress, ZMQ.SNDMORE) //First send address frame
    server.send("".getBytes(), ZMQ.SNDMORE) //Send empty frame
    server.send(ProtoBufConverter.toArray(msg))
  }

  def readMsgFromServerBroker(): (Array[Byte], Array[Byte]) = {
    //FOR PROTOCOL SEE BOOK OReilly ZeroMQ Messaging for any applications 2013 ~page 100
    //From server broker messanger we get msg with such body:
    //indentity frame
    // empty frame --> delimiter
    // data ->
    val clientAdrress = server.recv(0) //Indentity of client object on server
    if clientAdrress == null then throw new BrakeException()
    if server.recv(0) == null then throw new BrakeException()//empty frame
    (clientAdrress, server.recv(0))
  }

}

case class AppArgs(arguments: Seq[String]) extends ScallopConf(arguments) {

  import org.rogach.scallop.stringConverter
  import org.rogach.scallop.intConverter

  val port = opt[Int](required = true)
  val host = opt[String](required = true)
  verify()
}
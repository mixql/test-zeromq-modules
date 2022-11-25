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
  var appArgs: AppArgs = null

  def main(args: Array[String]): Unit = {
    appArgs = AppArgs(args)
    val indentity = String(appArgs.identity.toOption.get.getBytes)
    import org.rogach.scallop.ScallopConfBase
    val host: String = appArgs.host.toOption.get
    val port = appArgs.port.toOption.get
    println(s"Module $indentity: Starting main client")


    println(s"Module $indentity: host of server is " + host + " and port is " + port.toString)

    try {
      ctx = ZMQ.context(1)
      server = ctx.socket(SocketType.REQ)
      //set identity to our socket, if it would not be set,
      // then it would be generated by ROUTER socket in broker object on server

      server.setIdentity(appArgs.identity.toOption.get.getBytes)
      server.connect(s"tcp://$host:${port.toString}")
      println(s"Module $indentity: Connection established.")
      println(s"Module $indentity: Send msg to server that i am ready")
      server.send("READY")
      while (true) {
        val rs = readMsgFromServerBroker()
        val clientAdrress = rs._1
        val clientAdressStr = String(clientAdrress)
        println(s"Module $indentity: have received message from server ${clientAdressStr}")
        val msg = rs._2
        ProtoBufConverter.toProtobuf(msg) match {
          case ZioMsgTest1(msg, msg2, msg3, _) =>
            println(s"Module $indentity: Received ZioMsgTest1 msg from server: ${msg} ${msg2} ${msg3}")
            println(s"Module $indentity: Sending reply on ZioMsgTest1 msg")
            sendMsgToServerBroker(clientAdrress, ZioMsgTestReply(s"Module $indentity to ${clientAdressStr}: " +
              "successfully received ZioMsgTest1"))
          case ZioMsgTest2Array(messages, _) =>
            println(s"Module $indentity :Received ZioMsgTest2Array msg from server $clientAdressStr: " +
              s"${messages.mkString(" ")}")
            println(s"Module $indentity: Sending reply on ZioMsgTest2Array msg")
            sendMsgToServerBroker(clientAdrress, ZioMsgTestReply(s"Module $indentity to ${clientAdressStr}: " +
              "successfully received ZioMsgTest2Array"))
          case ZioMsgTest3Map(msgMap, _) =>
            println(s"Module $indentity: Received ZioMsgTest3Map msg from server: ${msgMap.mkString(" ")}")
            println(s"Module $indentity:  Sending reply on ZioMsgTest3Map msg")
            sendMsgToServerBroker(clientAdrress, ZioMsgTestReply(s"Module $indentity to ${clientAdressStr}: " +
              "successfully received ZioMsgTest3Map"))
          case ShutDown(_) =>
            println(s"Module $indentity: Started shutdown")
            throw BrakeException()
        }
      }
    } catch {
      case _: BrakeException =>
      case ex: Exception =>
        println(s"Module $indentity: Error: " + ex.getMessage)
    } finally {
      if server != null then
        server.close()
      if ctx != null then
        ctx.term()
    }
  }

  def sendMsgToServerBroker(clientAdrress: Array[Byte], msg: scalapb.GeneratedMessage) = {
    val indentity = String(appArgs.identity.toOption.get.getBytes)
    //Sending multipart message
    println(s"Module $indentity: sendMsgToServerBroker: sending clientaddress")
    server.send(clientAdrress, ZMQ.SNDMORE) //First send address frame
    println(s"Module $indentity: sendMsgToServerBroker: sending empty frame")
    server.send("".getBytes(), ZMQ.SNDMORE) //Send empty frame
    println(s"Module $indentity: sendMsgToServerBroker: sending protobuf message")
    server.send(ProtoBufConverter.toArray(msg))
  }

  def readMsgFromServerBroker(): (Array[Byte], Array[Byte]) = {
    //FOR PROTOCOL SEE BOOK OReilly ZeroMQ Messaging for any applications 2013 ~page 100
    //From server broker messanger we get msg with such body:
    //indentity frame
    // empty frame --> delimiter
    // data ->
    val indentity = String(appArgs.identity.toOption.get.getBytes)
    val clientAdrress = server.recv(0) //Indentity of client object on server
    if clientAdrress == null then throw new BrakeException()
    println(s"$indentity readMsgFromServerBroker: got client address: " + String(clientAdrress))
    if server.recv(0) == null then throw new BrakeException() //empty frame
    println(s"$indentity readMsgFromServerBroker: received empty frame")
    (clientAdrress, server.recv(0))
  }

}

case class AppArgs(arguments: Seq[String]) extends ScallopConf(arguments) {

  import org.rogach.scallop.stringConverter
  import org.rogach.scallop.intConverter

  val port = opt[Int](required = true)
  val host = opt[String](required = true)
  val identity = opt[String](required = true)
  verify()
}

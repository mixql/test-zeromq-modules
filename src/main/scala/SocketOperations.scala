import java.nio.channels.{SocketChannel}
import java.nio.ByteBuffer

//TO_DO Use onebuffer, using flip and clear functions
object SocketOperations {
  def readMsgFromSocket(server: SocketChannel): scalapb.GeneratedMessage = {
    val protoBufMsgLength = readIntFromSocket(server)
    val protoBufMsg: Array[Byte] = readProtoBufFromSocket(server, protoBufMsgLength)
    println("server: readMsgFromSocket: File Received. Converting array of bytes of size " +
      protoBufMsg.length + " to Protobuf msg")
    ProtoBufConverter.toProtobuf(protoBufMsg)
  }

  def readIntFromSocket(server: SocketChannel): Int = {
    import java.nio.ByteBuffer
    val bytes = new Array[Byte](4)
    val buffer = ByteBuffer.wrap(bytes)
    //Block and wait for answer
    //    var res = -1
    //    while (res < 0) {
    buffer.clear
    val res = server.read(buffer)
    println("server: readIntSocket: " + res)
    if (res == -1) throw Exception("server: Connection was closed")
    //    }
    val returnValue: Int = convertByteArrayToInt(bytes)
    buffer.clear
    println("server: readIntSocket: got int " + returnValue)
    returnValue
  }

  def readProtoBufFromSocket(server: SocketChannel, length: Int): Array[Byte] = {
    val buffer = ByteBuffer.allocate(length)

    //Block and wait for answer
    var res = -1
    //    while (res < 0) {
    buffer.clear
    res = server.read(buffer)
    println("server: readMsgFromSocket: " + res)
    //    }
    if (res == -1) throw Exception("server: Connection was closed")
    val protoBufMsg: Array[Byte] = buffer.array()
    buffer.clear
    protoBufMsg
  }

  def writeMsgToSocket(server: SocketChannel, msg: scalapb.GeneratedMessage) = {
    System.out.println(s"server: convert ${msg.getClass.getName} to array of bytes")
    var protoBufMsg: Array[Byte] = ProtoBufConverter.toArray(msg)
    println("\"server: \" writeMsgToSocket: size of protobufMsg: " + protoBufMsg.length)
    writeIntToSocket(server, protoBufMsg.length)
    writeProtoBufToSocket(server, protoBufMsg)
    System.out.println(s"server: File ${msg.getClass.getName} was Sent")
  }

  def writeIntToSocket(server: SocketChannel, i: Int) = {
    System.out.println("server: Sending int to client")
    import java.nio.ByteBuffer
    val bb = ByteBuffer.wrap(intToBytes(i))
    val res = server.write(bb)
    System.out.println("server: " + res)
    bb.clear
    System.out.println("server: Int was Sent")
  }

  def writeProtoBufToSocket(server: SocketChannel, protoBufMsg: Array[Byte]) = {
    import java.nio.ByteBuffer
    val buffer = ByteBuffer.wrap(protoBufMsg)
    val res = server.write(buffer)
    System.out.println("server: " + res)
    buffer.clear
  }

  def intToBytes(data: Int) = Array[Byte](
    ((data >> 24) & 0xff).toByte,
    ((data >> 16) & 0xff).toByte,
    ((data >> 8) & 0xff).toByte,
    ((data >> 0) & 0xff).toByte
  )

  def convertByteArrayToInt(data: Array[Byte]): Int = {
    if (data == null || data.length != 4) return 0x0
    // ----------
    ((0xff & data(0)) << 24 | (0xff & data(1)) << 16 | (0xff & data(2)) << 8 | (0xff & data(3)) << 0).toInt
  }
}

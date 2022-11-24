import app.zio.grpc.remote.clientMsgs.*

object ProtoBufConverter {
  def toProtobuf(array: Array[Byte]): scalapb.GeneratedMessage = {
    val anyMsg = AnyMsg.parseFrom(array)
    anyMsg.`type` match {
      case "app.zio.grpc.remote.clientMsgs.ZioMsgTestReply" => anyMsg.getMsg.unpack[ZioMsgTestReply]
      case "app.zio.grpc.remote.clientMsgs.ShutDown" => anyMsg.getMsg.unpack[ShutDown]
      case "app.zio.grpc.remote.clientMsgs.ZioMsgTest1" => anyMsg.getMsg.unpack[ZioMsgTest1]
      case "app.zio.grpc.remote.clientMsgs.ZioMsgTest2Array" => anyMsg.getMsg.unpack[ZioMsgTest2Array]
      case "app.zio.grpc.remote.clientMsgs.ZioMsgTest3Map" => anyMsg.getMsg.unpack[ZioMsgTest3Map]
      case a: Any => throw Exception(s"Protobuf converter: Error: Got unknown type ${anyMsg.`type`} of message")
    }
  }

  def toArray(msg: scalapb.GeneratedMessage): Array[Byte] = {
    AnyMsg(
      msg.getClass.getName,
      Some(com.google.protobuf.any.Any.pack(msg))
    ).toByteArray
  }
}

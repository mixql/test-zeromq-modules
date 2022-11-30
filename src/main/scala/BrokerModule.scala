import org.zeromq.{SocketType, ZMQ}

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

object BrokerModule {
  var ctx: ZMQ.Context = null
  var frontend: ZMQ.Socket = null
  var backend: ZMQ.Socket = null
  var poller: ZMQ.Poller = null
  var threadBroker: Thread = null

  //Key is identity, Value is list of messages
  val enginesStashedMsgs: mutable.Map[String, ListBuffer[StashedClientMessage]] = mutable.Map()
  val engines: mutable.Set[String] = mutable.Set()
  val NOFLAGS = 0
}

class BrokerModule(portFrontend: Int, portBackend: Int, host: String) extends java.lang.AutoCloseable {

  import BrokerModule.*

  def start() = {
    if threadBroker == null then
      println("Starting broker thread")
      threadBroker = new BrokerMainRunnable("BrokerMainThread", host, portFrontend.toString,
        portBackend.toString)
      threadBroker.start()
  }

  override def close() = {
    if (threadBroker != null && threadBroker.isAlive() && !threadBroker.isInterrupted)
      println("Broker: Executing close")
      println("Broker: send interrupt to thread")
      threadBroker.interrupt()
    //      println("Waiting while broker thread is alive")
    //      try {
    //        threadBroker.join();
    //      }
    //      catch
    //        case _: InterruptedException => System.out.printf("%s has been interrupted", threadBroker.getName())
    //      println("server: Broker was shutdown")
  }
}

class BrokerMainRunnable(name: String, host: String, portFrontend: String, portBackend: String) extends Thread(name) {

  import BrokerModule.*

  def init(): (Int, Int) = {
    println("Initialising broker")
    ctx = ZMQ.context(1)
    frontend = ctx.socket(SocketType.ROUTER)
    backend = ctx.socket(SocketType.ROUTER)
    println("Broker: starting frontend router socket on " + portFrontend.toString)
    frontend.bind(s"tcp://$host:${portFrontend.toString}")
    println("Broker: starting backend router socket on " + portBackend.toString)
    backend.bind(s"tcp://$host:${portBackend.toString}")
    println("Initialising poller")
    poller = ctx.poller(2)
    val polBackendIndex = poller.register(backend, ZMQ.Poller.POLLIN)
    val polFrontendIndex = poller.register(frontend, ZMQ.Poller.POLLIN)
    println("initialised brocker")
    println("broker : polBackendIndex: " + polBackendIndex +
      " polFrontendIndex: " + polFrontendIndex
    )
    (polBackendIndex, polFrontendIndex)
  }

  override def run(): Unit = {
    val initRes = init()
    println("Broker thread was started")
    try {
      while (!Thread.currentThread().isInterrupted()) {
        val rc = poller.poll(1000)
        if (rc == -1) throw Exception("brake")
        println("ThreadInterrupted: " + Thread.currentThread().isInterrupted())
        //Receive messages from engines
        if (poller.pollin(initRes._1)) {
          val (workerAddrStr, ready, clientIDStr, msg, pingHeartBeatMsg) = receiveMessageFromBackend()
          ready match
            case Some(_) => //Its READY message from engine
              if !engines.contains(workerAddrStr) then
                println(s"Broker: Add $workerAddrStr as key in engines set")
                engines.add(workerAddrStr) //only this thread will write, so there will be no race condition
                sendStashedMessagesToBackendIfTheyAre(workerAddrStr)
            case None => //its message from engine to client or heart beat message from engine
              pingHeartBeatMsg match {
                case Some(_) => //Its heart beat message from engine
                  sendMessageToBackend(s"Broker backend heart beat pong:", workerAddrStr,
                    "PONG-HEARTBEAT".getBytes)
                case None => //its message from engine to client
                  sendMessageToFrontend(clientIDStr.get, msg.get)
              }
          end match
        }
        if (poller.pollin(initRes._2)) {
          val (clientAddrStr, engineIdentityStr, request) = receiveMessageFromFrontend()
          if !engines.contains(engineIdentityStr) then
            println(s"Broker frontend: engine was not initialized yet. Stash message in engines map")
            stashMessage(engineIdentityStr, clientAddrStr, request)
          else
            sendMessageToBackend("Broker frontend", engineIdentityStr, clientAddrStr, request)
        }
      }
    }
    catch {
      case e: Throwable => println("Broker main thread: Got Exception: " + e.getMessage)
    } finally {
      if (backend != null) {
        println("Broker: closing backend")
        backend.close()
      }
      if frontend != null then
        println("Broker: closing frontend")
        frontend.close()

      if poller != null then {
        println("Broker: close poll")
        poller.close()
      }

      try {
        if ctx != null then {
          println("Broker: terminate context")
          //          ctx.term()
          ctx.close()
        }
      } catch {
        case e: Throwable => println("Warning error while closing broker context: " + e.getMessage)
      }
    }
    println("Broker thread finished...")
  }

  def receiveMessageFromBackend(): (String, Option[String], Option[String], Option[Array[Byte]], Option[String]) = {
    //FOR PROTOCOL SEE BOOK OReilly ZeroMQ Messaging for any applications 2013 ~page 100
    val workerAddr = backend.recv(NOFLAGS) //Received engine module identity frame
    val workerAddrStr = String(workerAddr)
    println(s"Broker backend : received identity $workerAddrStr from engine module")
    backend.recv(NOFLAGS) //received empty frame
    println(s"Broker backend : received empty frame  from engine module $workerAddrStr")
    //Third frame is READY message or client identity frame or heart beat message from engine
    val clientID = backend.recv(NOFLAGS)
    var clientIDStr: Option[String] = Some(String(clientID))
    var msg: Option[Array[Byte]] = None
    var ready: Option[String] = None
    var pingHeartBeat: Option[String] = None

    if clientIDStr.get != "READY" then
      if (clientIDStr.get == "PING-HEARTBEAT") then
        println(s"Broker: received PING-HEARTBEAT msg from engine module $workerAddrStr")
        pingHeartBeat = Some(clientIDStr.get)
      else
        //Its client's identity
        println(s"Broker backend : received client's identity $clientIDStr")
        backend.recv(NOFLAGS) //received empty frame
        println(s"Broker backend : received empty frame  from engine module $workerAddrStr")
        msg = Some(backend.recv(NOFLAGS))
        println(s"Broker backend : received protobuf message from engine module $workerAddrStr")
      end if
    else
      println(s"Broker: received READY msg from engine module $workerAddrStr")
      ready = Some(clientIDStr.get)
      clientIDStr = None
    end if

    (workerAddrStr, ready, clientIDStr, msg, pingHeartBeat)
  }

  def receiveMessageFromFrontend(): (String, String, Array[Byte]) = {
    val clientAddr = frontend.recv()
    val clientAddrStr = String(clientAddr)
    println("Broker frontend: received client's identity " + clientAddrStr)
    frontend.recv()
    println(s"Broker frontend: received empty frame from $clientAddrStr")
    val engineIdentity = frontend.recv()
    val engineIdentityStr = String(engineIdentity)
    println(s"Broker frontend: received engine module identity $engineIdentityStr from $clientAddrStr")
    frontend.recv()
    println(s"Broker frontend: received empty frame from $clientAddrStr")
    val request = frontend.recv()
    println(s"Broker frontend: received request for engine module $engineIdentityStr from $clientAddrStr")
    (clientAddrStr, engineIdentityStr, request)
  }

  def sendMessageToFrontend(clientIDStr: String, msg: Array[Byte]) = {
    println(s"Broker backend : sending clientId $clientIDStr to frontend")
    frontend.send(clientIDStr.getBytes, ZMQ.SNDMORE)
    println(s"Broker backend : sending empty frame to frontend")
    frontend.send("".getBytes, ZMQ.SNDMORE)
    println(s"Broker backend : sending protobuf message to frontend")
    frontend.send(msg)
  }

  def sendMessageToBackend(logMessagePrefix: String, engineIdentityStr: String, clientAddrStr: String,
                           request: Array[Byte]) = {
    println(s"$logMessagePrefix: sending $engineIdentityStr from $clientAddrStr to backend")
    backend.send(engineIdentityStr.getBytes, ZMQ.SNDMORE)
    println(s"$logMessagePrefix: sending epmpty frame to $engineIdentityStr from $clientAddrStr to backend")
    backend.send("".getBytes(), ZMQ.SNDMORE)
    println(s"$logMessagePrefix: sending clientAddr to $engineIdentityStr from $clientAddrStr to backend")
    backend.send(clientAddrStr.getBytes, ZMQ.SNDMORE)
    println(s"$logMessagePrefix: sending epmpty frame to $engineIdentityStr from $clientAddrStr to backend")
    backend.send("".getBytes(), ZMQ.SNDMORE)
    println(s"$logMessagePrefix: sending protobuf frame to $engineIdentityStr from $clientAddrStr to backend")
    backend.send(request, NOFLAGS)
  }

  def sendMessageToBackend(logMessagePrefix: String, engineIdentityStr: String, request: Array[Byte]) = {
    println(s"$logMessagePrefix: sending $engineIdentityStr  to backend")
    backend.send(engineIdentityStr.getBytes, ZMQ.SNDMORE)
    println(s"$logMessagePrefix: sending epmpty frame to $engineIdentityStr to backend")
    backend.send("".getBytes(), ZMQ.SNDMORE)
    println(s"$logMessagePrefix: sending message frame to $engineIdentityStr to backend")
    backend.send(request, NOFLAGS)
  }

  def sendStashedMessagesToBackendIfTheyAre(workerAddrStr: String) = {
    println(s"Broker: Check if there are stashed messages for our engine")
    enginesStashedMsgs.get(workerAddrStr) match
      case Some(messages) =>
        if messages.isEmpty then
          println(s"Broker: Checked engines map. No stashed messages for $workerAddrStr")
        else
          println(s"Broker: Have founded stashed messages (amount: ${messages.length}) " +
            s"for engine $workerAddrStr. Sending them")
          messages.foreach(
            msg =>
              sendMessageToBackend("Broker stashed: ", workerAddrStr, msg.ClientAddr, msg.request)
          )
          //                  engines.put(workerAddrStr, ListBuffer())
          messages.clear()

      case None => println(s"Broker: warning! no key $workerAddrStr, thow it should be here! Strange!")
    end match
  }

  def stashMessage(engineIdentityStr: String, clientAddrStr: String, request: Array[Byte]) = {
    if enginesStashedMsgs.get(engineIdentityStr).isEmpty then
      enginesStashedMsgs.put(engineIdentityStr, ListBuffer(StashedClientMessage(clientAddrStr, request)))
    else
      enginesStashedMsgs.get(engineIdentityStr) match
        case Some(messages) =>
          messages += StashedClientMessage(clientAddrStr, request)
        case None => println(s"Broker frontend: warning! no key $engineIdentityStr in enginesStashedMsgs," +
          s" thow it should be here! Strange!")
          enginesStashedMsgs.put(engineIdentityStr, ListBuffer(StashedClientMessage(clientAddrStr, request)))
      end match
    end if
  }
}

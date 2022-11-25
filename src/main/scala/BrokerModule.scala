import org.zeromq.{SocketType, ZMQ}

import scala.collection.mutable

object BrokerModule {
  var ctx: ZMQ.Context = null
  var frontend: ZMQ.Socket = null
  var backend: ZMQ.Socket = null
  var poller: ZMQ.Poller = null
  var threadBroker: Thread = null
  private var _portFrontend = -1
  private var _portBackend = -1

  def getPortFrontend = _portFrontend

  def getPortBackend = _portBackend
}

class BrokerModule(portFrontend: Int, portBackend: Int, host: String) extends java.lang.AutoCloseable {

  import BrokerModule.*

  def init() = {
    if ctx == null then
      println("Initialising broker")
      ctx = ZMQ.context(1)
      frontend = ctx.socket(SocketType.ROUTER)
      backend = ctx.socket(SocketType.ROUTER)
      println("Broker: starting frontend router socket on " + portFrontend.toString)
      frontend.bind(s"tcp://$host:${portFrontend.toString}")
      println("Broker: starting backend router socket on " + portBackend.toString)
      frontend.bind(s"tcp://$host:${portBackend.toString}")
      _portBackend = portBackend
      _portFrontend = portFrontend
      println("Initialising poller")
      poller = ctx.poller(2)
      poller.register(backend, ZMQ.Poller.POLLIN)
      poller.register(frontend, ZMQ.Poller.POLLIN)
      println("initialised brocker")
  }

  def start() = {
    init()
    if threadBroker != null then
      println("Starting broker thread")
      threadBroker = new Thread(new BrokerMainRunnable, "BrokerMainThread")
      threadBroker.start()
  }

  override def close() = {
    println("Broker: Executing close")
    if (threadBroker != null && threadBroker.isAlive())
      threadBroker.interrupt()
      println("Waiting while broker thread is alive")
      try {
        threadBroker.join();
      }
      catch
        case _: InterruptedException => System.out.printf("%s has been interrupted", threadBroker.getName())
    println("server: Broker was shutdown")
  }
}

class BrokerMainRunnable extends Runnable {

  import BrokerModule.*

  override def run(): Unit = {
    val NOFLAGS = 0
    println("Broker thread was started")
    try {
      while (!Thread.currentThread().isInterrupted()) {
        val rc = poller.poll
        if (rc == -1) throw Exception("brake")

        //Receive messages from engines
        if (poller.pollin(0)) {
          //FOR PROTOCOL SEE BOOK OReilly ZeroMQ Messaging for any applications 2013 ~page 100
          val workerAddr = backend.recv(NOFLAGS) //Received engine module identity frame
          val workerAddrStr = String(workerAddr)
          println(s"Broker backend : received identity $workerAddrStr from engine module")
          backend.recv(NOFLAGS) //received empty frame
          println(s"Broker backend : received empty frame  from engine module $workerAddrStr")
          //Third frame is READY protobuf message or client identity frame
          val clientID = backend.recv(NOFLAGS)
          val clientIDStr = String(clientID)
          if String(clientID) != "READY" then
            //Its client's identity
            println(s"Broker backend : received client's identity $clientIDStr")
            backend.recv(NOFLAGS) //received empty frame
            println(s"Broker backend : received empty frame  from engine module $workerAddrStr")
            val msg = backend.recv(NOFLAGS)
            println(s"Broker backend : received protobuf message from engine module $workerAddrStr")
            println(s"Broker backend : sending clientId $clientIDStr to frontend")
            frontend.send(clientID, ZMQ.SNDMORE)
            println(s"Broker backend : sending empty frame to frontend")
            frontend.send("".getBytes, ZMQ.SNDMORE)
            println(s"Broker backend : sending protobuf message to frontend")
            frontend.send(msg)
          else
            println(s"Broker: received READY msg from engine module $workerAddrStr")
          end if

          if (poller.pollin(1)) {
            val clientAddr = frontend.recv()
            val clientAddrStr = String(clientAddr)
            println("Broker frontend: received client's identity " + clientAddrStr)
            frontend.recv()
            println(s"Broker frontend: received empty frame from $clientAddrStr")
            val engineIdentity = frontend.recv()
            val engineIdentityStr = String(engineIdentity)
            println(s"Broker frontend: received engine module identity $engineIdentityStr from $clientAddrStr")
            val request = frontend.recv()
            println(s"Broker frontend: received request for engine module $engineIdentityStr from $clientAddrStr")

            println(s"Broker frontend: sending $engineIdentityStr from $clientAddrStr to backend")
            backend.send(engineIdentity, ZMQ.SNDMORE)
            println(s"Broker frontend: sending epmpty frame to $engineIdentityStr from $clientAddrStr to backend")
            backend.send("".getBytes(), ZMQ.SNDMORE)
            println(s"Broker frontend: sending clientAddr to $engineIdentityStr from $clientAddrStr to backend")
            backend.send(clientAddr, ZMQ.SNDMORE)
            println(s"Broker frontend: sending epmpty frame to $engineIdentityStr from $clientAddrStr to backend")
            backend.send("".getBytes(), ZMQ.SNDMORE)
            println(s"Broker frontend: sending protobuf frame to $engineIdentityStr from $clientAddrStr to backend")
            backend.send(request, NOFLAGS)
          }
        }
      }
    }
    catch {
      case e: Throwable => println("Broker main thread: Got Exception: " + e.getMessage)
    }

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

    if ctx != null then {
      println("Broker: terminate context")
      ctx.term()
    }
  }
}

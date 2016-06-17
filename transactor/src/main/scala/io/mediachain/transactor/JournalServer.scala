package io.mediachain.transactor

import com.amazonaws.auth.BasicAWSCredentials
import org.slf4j.{Logger, LoggerFactory}
import io.atomix.catalyst.transport.Address
import io.atomix.copycat.server.CopycatServer
import io.mediachain.copycat.{Server, Transport}
import io.mediachain.datastore.{PersistentDatastore, DynamoDatastore}
import io.mediachain.util.Properties
import scala.io.StdIn
import scala.collection.JavaConversions._
import sys.process._

object JournalServer {
  val logger = LoggerFactory.getLogger("io.mediachain.transactor.JournalServer")

  def main(args: Array[String]) {
    val (config, cluster) = parseArgs(args)
    val props = Properties.load(config)
    run(props, cluster)
  }
  
  def parseArgs(args: Array[String]) = {
    args.toList match {
      case config :: cluster =>
        (config, cluster.toList)
      case _ =>
        throw new RuntimeException("Expected arguments: config [cluster-address ...]")
    }
  }

  def run(conf: Properties, cluster: List[String]) {
    val rootdir = conf.getq("io.mediachain.transactor.server.rootdir")
    (s"mkdir -p $rootdir").!
    val ctldir = rootdir + "/ctl"
    val copycatdir = rootdir + "/copycat"
    (s"mkdir $copycatdir").!
    val rockspath = rootdir + "/rocks.db"
    val address = conf.getq("io.mediachain.transactor.server.address")
    val sslConfig = Transport.SSLConfig.fromProperties(conf)
    val dynamoConfig = DynamoDatastore.Config.fromProperties(conf)
    val datastore = new PersistentDatastore(PersistentDatastore.Config(dynamoConfig, rockspath))
    val server = Server.build(address, copycatdir, datastore, sslConfig)

    datastore.start    
    if (cluster.isEmpty) {
      server.bootstrap.join()
    } else {
      server.join(cluster.map {addr => new Address(addr)}).join()
    }
    
    serverControlLoop(ctldir, server, datastore)
  }
  
  def serverControlLoop(ctldir: String, 
                        server: CopycatServer, 
                        datastore: PersistentDatastore) {
    def shutdown(what: String) {
      logger.info("Shutting down server")
      server.shutdown.join()
      quit()
    }
    
    def leave(what: String) {
      logger.info("Leaving the cluster")
      server.leave.join()
      quit()
    }
    
    def quit() {
      datastore.close()
      System.exit(0)
    }
    
    val commands = Map(
      "shutdown" -> shutdown _,
      "leave" -> leave _
    )
    val ctl = ServerControl.build(ctldir, commands)
    ctl.run
  }

  def run(config: Config) {
    val props = new Properties()
    props.put("io.mediachain.transactor.server.rootdir",
      config.transactorDataDir.getAbsolutePath)
    props.put("io.mediachain.transactor.server.address",
      config.listenAddress.asString)
    props.put("io.mediachain.transactor.dynamo.baseTable",
      config.dynamoConfig.baseTable)
    config.dynamoConfig.endpoint.foreach { endpoint =>
      props.put("io.mediachain.transactor.dynamo.endpoint",
        endpoint)
    }
    val cluster = config.clusterAddresses.map(_.asString).toList
    run(props, cluster)
  }
}

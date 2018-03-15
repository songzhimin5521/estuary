package com.neighborhood.aka.laplace.estuary.mysql.lifecycle

import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{Actor, ActorLogging, OneForOneStrategy, Props}
import com.neighborhood.aka.laplace.estuary.bean.key.BinlogKey
import com.neighborhood.aka.laplace.estuary.bean.support.KafkaMessage
import com.neighborhood.aka.laplace.estuary.core.lifecycle
import com.neighborhood.aka.laplace.estuary.core.lifecycle.Status.Status
import com.neighborhood.aka.laplace.estuary.core.lifecycle.{SinkerMessage, SourceDataSinker, Status, SyncControllerMessage}
import com.neighborhood.aka.laplace.estuary.core.task.TaskManager
import com.neighborhood.aka.laplace.estuary.mysql.Mysql2KafkaTaskInfoManager
import org.I0Itec.zkclient.exception.ZkTimeoutException
import org.apache.kafka.clients.producer.{Callback, RecordMetadata}
import org.springframework.util.StringUtils

/**
  * Created by john_liu on 2018/2/9.
  */
class ConcurrentBinlogSinker(mysql2KafkaTaskInfoManager: Mysql2KafkaTaskInfoManager) extends Actor with SourceDataSinker with ActorLogging {

  implicit val sinkTaskPool = new collection.parallel.ForkJoinTaskSupport(new scala.concurrent.forkjoin.ForkJoinPool(mysql2KafkaTaskInfoManager.taskInfo.batchThreshold.get().toInt))
  /**
    * 拼接json用
    */
  private val START_JSON = "{"
  private val END_JSON = "}"
  private val START_ARRAY = "["
  private val END_ARRAY = "]"
  private val KEY_VALUE_SPLIT = ":"
  private val ELEMENT_SPLIT = ","
  private val STRING_CONTAINER = "\""
  /**
    * kafkaSinker
    */
  val kafkaSinker = mysql2KafkaTaskInfoManager.kafkaSink
  /**
    * logPosition处理
    */
  val logPositionHandler = mysql2KafkaTaskInfoManager.logPositionHandler
  /**
    * 同步标识作为写入zk 的标识
    */
  val destination = mysql2KafkaTaskInfoManager.taskInfo.syncTaskId
  /**
    * 本次同步任务开始的logPosition
    * 从zk中获取
    */
  val startPosition = Option(logPositionHandler.logPositionManager.getLatestIndexBy(destination))
  /**
    * 是否发生异常
    */
  val isAbnormal = new AtomicBoolean(false)
  /**
    * 待保存的BinlogOffset
    */
  var lastSavedOffset: Long = if (startPosition.isDefined) {
    startPosition.get.getPostion.getPosition
  } else 4L
  /**
    * 待保存的Binlog文件名称
    */
  var lastSavedJournalName: String = if (startPosition.isDefined) {
    startPosition.get.getPostion.getJournalName
  } else ""

  var isCounting = mysql2KafkaTaskInfoManager.taskInfo.isCounting
 //  lazy val theBatchCount = new AtomicLong(0)

  //offline
  override def receive: Receive = {
    case SyncControllerMessage(msg) => {
      msg match {
        case "start" => {
          //online模式
          log.info("sinker swtich to online")
          context.become(online)
          sinkerChangeStatus(Status.ONLINE)
        }
        case x => {
          log.warning(s"sinker offline unhandled message:$x")
        }
      }
    }
  }

  def online: Receive = {
    case list: List[Any] => {

      /**
        * 待保存的BinlogOffset
        */
      var savedOffset: Long = 0L
      /**
        * 待保存的Binlog文件名称
        */
      var savedJournalName: String = ""
      lazy val count = list.size
      val before = System.currentTimeMillis()
      val task = list.par
      task.tasksupport = sinkTaskPool
      task
        .map {
          x =>
            x match {
              case message: KafkaMessage => handleSinkTask(message)
              case messages: Array[KafkaMessage] => messages.map(handleSinkTask(_))

              case BinlogPositionInfo(journalName, offset) => {
                savedJournalName = journalName
                savedOffset = offset
              }
              case x => log.warning(s"sinker unhandled message:$x")
            }
        }

      val after = System.currentTimeMillis()
      //这次任务完成后
      log.info(s"send处理用了${after - before},s$lastSavedJournalName:$lastSavedOffset")
      if (isCounting) mysql2KafkaTaskInfoManager.sinkCount.addAndGet(count)
      //保存这次任务的binlog
      //判断的原因是如果本次写入没有事务offset就不记录
      if (!StringUtils.isEmpty(savedJournalName)) {
        this.lastSavedJournalName = savedJournalName
        this.lastSavedOffset = savedOffset
      }


      //   log.info(s"JournalName update to $savedJournalName,offset update to $savedOffset")

    }
    // 定时记录logPosition
    case SyncControllerMessage("record") => logPositionHandler.persistLogPosition(destination, lastSavedJournalName, lastSavedOffset)
    case x => {
      log.warning(s"sinker online unhandled message $x")

    }
  }

  /**
    *
    */
  def handleSinkTask(kafkaMessage: KafkaMessage, journalName: String = this.lastSavedJournalName, offset: Long = this.lastSavedOffset): Unit = {
    val before = System.currentTimeMillis()
    val key = s"${kafkaMessage.getBaseDataJsonKey.asInstanceOf[BinlogKey].getDbName}.${kafkaMessage.getBaseDataJsonKey.asInstanceOf[BinlogKey].getTableName}"
    val topic = kafkaSinker.findTopic(key)
    /**
      * 写数据时的异常
      */
    val callback = new Callback {
      val thisJournalName = lastSavedJournalName
      val thisOffset = lastSavedOffset

      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = {
        if (exception != null) {

          log.error("Error when send :" + key + ", metadata:" + metadata + exception + "lastSavedPoint" + s" thisJournalName = $thisJournalName" + s" thisOffset = $thisOffset")
          if (isAbnormal.compareAndSet(false, true)) {

            logPositionHandler.persistLogPosition(destination, thisJournalName, thisOffset)
            context.parent ! SinkerMessage("error")
            log.info("send to recorder lastSavedPoint" + s"thisJournalName = $thisJournalName" + s"thisOffset = $thisOffset")
            //todo 做的不好 ，应该修改一下messge模型

          }

          //          throw new RuntimeException(s"Error when send data to kafka the journalName:$thisJournalName,offset:$thisOffset")

        }
      }
    }

    // log.info(kafkaMessage.getJsonValue.substring(0, 5))
    //  kafkaSinker.ayncSink(kafkaMessage.getBaseDataJsonKey.asInstanceOf[BinlogKey], kafkaMessage.getJsonValue)(topic)(callback)
    val after = System.currentTimeMillis()

    // log.info(s"sink cost time :${after-before}")

  }


  override var errorCountThreshold: Int = 3
  override var errorCount: Int = 0

  /**
    * 错误处理
    */
  override def processError(e: Throwable, message: lifecycle.WorkerMessage): Unit = {
    //do nothing
  }

  /**
    * ********************* 状态变化 *******************
    */

  private def changeFunc(status: Status) = TaskManager.changeFunc(status, mysql2KafkaTaskInfoManager)

  private def onChangeFunc = Mysql2KafkaTaskInfoManager.onChangeStatus(mysql2KafkaTaskInfoManager)

  private def sinkerChangeStatus(status: Status) = TaskManager.changeStatus(status, changeFunc, onChangeFunc)


  /**
    * **************** Actor生命周期 *******************
    */
  override def preStart(): Unit = {
    sinkerChangeStatus(Status.OFFLINE)

  }

  override def postStop(): Unit = {
    if (!isAbnormal.get() && !StringUtils.isEmpty(lastSavedJournalName)) {
      val theJournalName = this.lastSavedJournalName
      val theOffset = this.lastSavedOffset
      logPositionHandler.persistLogPosition(destination, theJournalName, theOffset)
      log.info(s"记录binlog $theJournalName,$theOffset")
    }
    kafkaSinker.kafkaProducer.close()
    sinkTaskPool.environment.shutdown()
    //logPositionHandler.logPositionManage
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    sinkerChangeStatus(Status.RESTARTING)
    context.become(receive)
  }

  override def postRestart(reason: Throwable): Unit = {

    super.postRestart(reason)
  }

  override def supervisorStrategy = {
    OneForOneStrategy() {
      case e: StackOverflowError => {
        sinkerChangeStatus(Status.ERROR)
        log.error("stackOverFlow")
        Escalate
      }

      case e: ZkTimeoutException => {
        sinkerChangeStatus(Status.ERROR)
        log.error("can not connect to zookeeper server")
        Escalate
      }
      case e: Exception => {
        sinkerChangeStatus(Status.ERROR)
        Escalate
      }
      case error: Error => {
        sinkerChangeStatus(Status.ERROR)
        Escalate
      }
      case _ => {
        sinkerChangeStatus(Status.ERROR)
        Escalate
      }
    }
  }
}

object ConcurrentBinlogSinker {
  //  def prop(mysql2KafkaTaskInfoManager: Mysql2KafkaTaskInfoManager, binlogPositionRecorder: ActorRef): Props = {
  //    Props(new ConcurrentBinlogSinker(mysql2KafkaTaskInfoManager, binlogPositionRecorder))

  def prop(mysql2KafkaTaskInfoManager: Mysql2KafkaTaskInfoManager): Props = {
    Props(new ConcurrentBinlogSinker(mysql2KafkaTaskInfoManager))
  }

}
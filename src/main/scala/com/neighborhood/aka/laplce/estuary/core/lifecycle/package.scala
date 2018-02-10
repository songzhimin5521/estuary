package com.neighborhood.aka.laplce.estuary.core

/**
  * Created by john_liu on 2018/2/9.
  */
package object lifecycle {
  trait WorkerMessage{
    val msg:String
  }
  case class SyncControllerMessage(msg:String) extends WorkerMessage
  case class ListenerMessage(msg:String)extends WorkerMessage
  case class SinkerMessage(msg:String)extends WorkerMessage
  case class FetcherMessage(msg:String)extends WorkerMessage
  case class BatcherMessage(msg:String)extends WorkerMessage
}

package com.neighborhood.aka.laplace.estuary.core.lifecycle

/**
  * Created by john_liu on 2018/2/6.
  */
trait SourceDataBatcher extends worker{
  implicit val workerType = WorkerType.Batcher
}

package com.datahack.bootcamp.spark.streaming

import org.apache.spark.SparkContext
import org.apache.spark.sql.{Column, DataFrame, Dataset, SparkSession}
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._

import scala.concurrent.duration._
import org.apache.spark.sql.streaming.{ProcessingTime, StreamingQuery}
import org.apache.spark.sql.streaming.OutputMode.Complete

object SqlStreaming extends App {

  val spark: SparkSession = SparkSession.builder()
    .appName("Munging example")
    .master("local[2]")
    .enableHiveSupport()
    .getOrCreate()
  val sc: SparkContext = spark.sparkContext
  import spark.implicits._

  // Register a StreamingQueryListener to receive notifications about state changes of streaming queries
  import org.apache.spark.sql.streaming.StreamingQueryListener
  val myQueryListener = new StreamingQueryListener {
    import org.apache.spark.sql.streaming.StreamingQueryListener._
    def onQueryTerminated(event: QueryTerminatedEvent): Unit = {
      println(s"Query ${event.id} terminated")
    }

    def onQueryStarted(event: QueryStartedEvent): Unit = {}
    def onQueryProgress(event: QueryProgressEvent): Unit = {
      println(s"Query on progress ${event.progress}")
    }
  }
  spark.streams.addListener(myQueryListener)

  val bidSchema: StructType = new StructType()
    .add("bidid", StringType)
    .add("timestamp", StringType)
    .add("ipinyouid", StringType)
    .add("useragent", StringType)
    .add("IP", StringType)
    .add("region", IntegerType)
    .add("cityID", IntegerType)
    .add("adexchange", StringType)
    .add("domain", StringType)
    .add("turl", StringType)
    .add("urlid", StringType)
    .add("slotid", StringType)
    .add("slotwidth", StringType)
    .add("slotheight", StringType)
    .add("slotvisibility", StringType)
    .add("slotformat", StringType)
    .add("slotprice", StringType)
    .add("creative", StringType)
    .add("bidprice", StringType)

  val streamingInputDF: DataFrame = spark.readStream
    .format("csv")
    .schema(bidSchema)
    .option("header", false)
    .option("inferSchema", true)
    .option("sep", "\t")
    .option("maxFilesPerTrigger", 1)
    .load("file:///Users/rafaelgarrote/Downloads/ipinyou.contest.dataset-season2/training2nd")
  streamingInputDF.printSchema()

  //Code for Implementing sliding window-based functionality section
  val ts: Column = unix_timestamp($"timestamp", "yyyyMMddHHmmssSSS").cast("timestamp")
  val streamingCityTimeDF: DataFrame = streamingInputDF.withColumn("ts", ts).select($"cityID", $"ts")

  //Wait for the output show on the screen after the next statement
  val windowedCounts: StreamingQuery = streamingCityTimeDF
    .groupBy(window($"ts", "10 minutes", "5 minutes"), $"cityID")
    .count()
    .writeStream.outputMode("complete")
    .format("console").start()

  import java.util.concurrent.Executors
  import java.util.concurrent.TimeUnit.SECONDS
  def queryTerminator(query: StreamingQuery) = new Runnable {
    def run = {
      println(s"Stopping streaming query: ${query.id}")
      query.stop
    }
  }
  import java.util.concurrent.TimeUnit.SECONDS
  // Stop the first query after 10 seconds
  Executors.newSingleThreadScheduledExecutor.
    scheduleWithFixedDelay(queryTerminator(windowedCounts), 1200, 60 * 5, SECONDS)

  // Use StreamingQueryManager to wait for any query termination (either q1 or q2)
  // the current thread will block indefinitely until either streaming query has finished
  spark.streams.awaitAnyTermination

  /*Thread.sleep(120000)

  //Code for Joining a streaming dataset with a static dataset section
  val citySchema = new StructType().add("cityID", StringType).add("cityName", StringType)
  val staticDF: DataFrame = spark.read
    .format("csv")
    .schema(citySchema)
    .option("header", false)
    .option("inferSchema", true)
    .option("sep", "\t")
    .load("file:///Users/rafaelgarrote/Downloads/ipinyou.contest.dataset-season2/city.en.txt")
  val joinedDF = streamingCityTimeDF.join(staticDF, "cityID")

  //Wait for the output show on the screen after the next statement
  val windowedCityCounts: StreamingQuery = joinedDF
    .groupBy(window($"ts", "10 minutes", "5 minutes"), $"cityName")
    .count()
    .writeStream.outputMode("complete")
    .format("console").start()

  Thread.sleep(120000)

  val streamingCityNameBidsTimeDF = streamingInputDF.
    withColumn("ts", ts)
    .select($"ts", $"bidid", $"cityID", $"bidprice", $"slotprice")
    .join(staticDF, "cityID")

  //Wait for the output show on the screen after the next statement
  val cityBids: StreamingQuery = streamingCityNameBidsTimeDF
    .select($"ts", $"bidid", $"bidprice", $"slotprice", $"cityName")
    .writeStream.outputMode("append")
    .format("console")
    .start()

  Thread.sleep(120000)

  //Code for Using the Dataset API in Structured Streaming section
  case class Bid(
                  bidid: String,
                  timestamp: String,
                  ipinyouid: String,
                  useragent: String,
                  IP: String,
                  region: Integer,
                  cityID: Integer,
                  adexchange: String,
                  domain: String,
                  turl: String,
                  urlid: String,
                  slotid: String,
                  slotwidth: String,
                  slotheight: String,
                  slotvisibility: String,
                  slotformat: String,
                  slotprice: String,
                  creative: String,
                  bidprice: String)

  val ds: Dataset[Bid] = streamingInputDF.as[Bid]

  //Code for Using the Foreach Sink for arbitrary computations on output section
  import org.apache.spark.sql.ForeachWriter

  val writer = new ForeachWriter[String] {
    override def open(partitionId: Long, version: Long) = true
    override def process(value: String) = println(value)
    override def close(errorOrNull: Throwable) = {}
  }

  val dsForeach: StreamingQuery = ds.filter(_.adexchange == "3").map(_.useragent).writeStream.foreach(writer).start()

  //Code for Using the Memory Sink to save output to a table section
  val aggAdexchangeDF = streamingInputDF.groupBy($"adexchange").count()

  //Wait for the output show on the screen after the next statement
  val aggQuery = aggAdexchangeDF
    .writeStream.queryName("aggregateTable")
    .outputMode("complete")
    .format("memory")
    .start()

  spark.sql("select * from aggregateTable").show()

  //Code for Using the File Sink to save output to a partitioned table section
  val cityBidsParquet = streamingCityNameBidsTimeDF
    .select($"bidid", $"bidprice", $"slotprice", $"cityName")
    .writeStream.outputMode("append")
    .format("parquet")
    .option("path", "hdfs://localhost:9000/pout")
    .option("checkpointLocation", "hdfs://localhost:9000/poutcp")
    .start()

  //Code for Monitoring streaming queries section
  spark.streams.active
    .foreach(x => println("ID:"+ x.id + "             Run ID:"+ x.runId + "               Status: "+ x.status))

  // get the unique identifier of the running query that persists across restarts from checkpoint data
  windowedCounts.id
  // get the unique id of this run of the query, which will be generated at every start/restart
  windowedCounts.runId
  // the exception if the query has been terminated with error
  windowedCounts.exception
  // the most recent progress update of this streaming query
  windowedCounts.lastProgress

  windowedCounts.stop()*/

}

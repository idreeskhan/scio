package com.spotify.scio.io

import java.util.UUID

import com.google.api.services.bigquery.model.TableReference
import com.google.cloud.dataflow.sdk.options.DataflowPipelineOptions
import com.google.cloud.dataflow.sdk.util.CoderUtils
import com.spotify.scio.ScioContext
import com.spotify.scio.bigquery.{BigQueryClient, TableRow}
import com.spotify.scio.coders.KryoAtomicCoder
import com.spotify.scio.values.SCollection
import org.apache.avro.Schema

import scala.reflect.ClassTag

/**
 * Placeholder to an external data set that can be either read into memory or opened as an SCollection.
 */
trait Tap[T] {

  /** Read data set into memory. */
  def value: Iterator[T]

  /** Open data set as an SCollection. */
  def open(sc: ScioContext): SCollection[T]
}

/** Tap for text files on local file system or GCS. */
case class TextTap(path: String) extends Tap[String] {
  override def value: Iterator[String] = FileStorage(path).textFile
  override def open(sc: ScioContext): SCollection[String] = sc.textFile(path + "/part-*")
}
/** Tap for Avro files on local file system or GCS. */
case class AvroTap[T: ClassTag](path: String, schema: Schema = null) extends Tap[T] {
  override def value: Iterator[T] = FileStorage(path).avroFile(schema)
  override def open(sc: ScioContext): SCollection[T] = sc.avroFile[T](path + "/part-*")
}

/** Tap for JSON files on local file system or GCS. */
case class TableRowJsonTap(path: String) extends Tap[TableRow] {
  override def value: Iterator[TableRow] = FileStorage(path).tableRowJsonFile
  override def open(sc: ScioContext): SCollection[TableRow] = sc.tableRowJsonFile(path + "/part-*")
}

/** Tap for BigQuery tables. */
case class BigQueryTap(table: TableReference, opts: DataflowPipelineOptions) extends Tap[TableRow] {
  override def value: Iterator[TableRow] = BigQueryClient(opts.getProject, opts.getGcpCredential).getTableRows(table)
  override def open(sc: ScioContext): SCollection[TableRow] = sc.bigQueryTable(table)
}

/** Tap for object files on local file system or GCS. */
case class ObjectFileTap[T: ClassTag](path: String) extends Tap[T] {
  override def value: Iterator[T] = {
    val coder = KryoAtomicCoder[T]
    FileStorage(path).textFile.map(CoderUtils.decodeFromBase64(coder, _))
  }
  override def open(sc: ScioContext): SCollection[T] = sc.objectFile(path + "/part-*")
}

private[scio] class InMemoryTap[T: ClassTag] extends Tap[T] {
  private[scio] val id: String = UUID.randomUUID().toString
  override def value: Iterator[T] = InMemorySinkManager.get(id).iterator
  override def open(sc: ScioContext): SCollection[T] = sc.parallelize[T](InMemorySinkManager.get(id))
}
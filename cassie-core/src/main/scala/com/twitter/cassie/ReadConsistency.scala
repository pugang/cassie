package com.twitter.cassie

import org.apache.cassandra.finagle.thrift

/**
 * The level of consistency required for a read operation.
 */
sealed case class ReadConsistency(level: thrift.ConsistencyLevel) {
  override def toString = "ReadConsistency." +
    level.toString.toLowerCase.capitalize
}

object ReadConsistency {

  /**
   * Will return the record returned by the first node to respond. A consistency
   * check is sometimes done in a background thread to fix any consistency issues
   * when ReadConsistency.One is used (see read_repair_chance in cassandra). This
   * means eventuall subsequent calls will have correct data even if the initial read gets
   * an older value. (This is called read repair.)
   */
  val One = ReadConsistency(thrift.ConsistencyLevel.ONE)

  /**
   * Will query all nodes and return the record with the most recent timestamp
   * once it has at least a majority of replicas reported. Again, the remaining
   * replicas will be checked in the background.
   */
  val Quorum = ReadConsistency(thrift.ConsistencyLevel.QUORUM)

  /**
   * Returns the record with the most recent timestamp once a majority of replicas within
   * the local datacenter have replied. Requres NetworkTopologyStrategy on the server side.
   */
  val LocalQuorum = ReadConsistency(thrift.ConsistencyLevel.LOCAL_QUORUM)

  /**
   * Returns the record with the most recent timestamp once a majority of replicas within
   * each datacenter have replied.
   */
  val EachQuorum = ReadConsistency(thrift.ConsistencyLevel.EACH_QUORUM)

  /**
   * Will query all nodes and return the record with the most recent timestamp
   * once all nodes have replied. Any unresponsive nodes will fail the
   * operation.
   */
  val All = ReadConsistency(thrift.ConsistencyLevel.ALL)
}

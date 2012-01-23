package com.twitter.cassie.tests

import scala.collection.JavaConversions._
import org.scalatest.Spec
import org.scalatest.matchers.MustMatchers
import org.scalatest.mock.MockitoSugar
import com.twitter.cassie.codecs.Utf8Codec
import org.mockito.Mockito.{ when, verify }
import org.mockito.Matchers.{ eq => matchEq, anyListOf }
import org.apache.cassandra.finagle.thrift
import org.mockito.ArgumentCaptor
import java.nio.ByteBuffer
import com.twitter.cassie._

import com.twitter.cassie.util.ColumnFamilyTestHelper
import com.twitter.util.Future
import java.util.{ ArrayList => JArrayList }

class CounterColumnFamilyTest extends Spec with MustMatchers with MockitoSugar with ColumnFamilyTestHelper {

  describe("getting a columns for a key") {
    val (client, cf) = setupCounters

    it("performs a get_counter_slice with a set of column names") {
      cf.getColumn("key", "name")

      val cp = new thrift.ColumnParent("cf")

      val pred = ArgumentCaptor.forClass(classOf[thrift.SlicePredicate])

      verify(client).get_slice(matchEq(b("key")), matchEq(cp), pred.capture, matchEq(thrift.ConsistencyLevel.QUORUM))

      pred.getValue.getColumn_names.map { Utf8Codec.decode(_) } must equal(List("name"))
    }

    it("returns none if the column doesn't exist") {
      when(client.get_slice(anyByteBuffer(), anyColumnParent(), anySlicePredicate(),
        anyConsistencyLevel()))
        .thenReturn(Future.value(new JArrayList[thrift.ColumnOrSuperColumn]()))
      cf.getColumn("key", "name")() must equal(None)
    }

    it("returns a option of a column if it exists") {
      val columns = Seq(cc("cats", 2L))

      when(client.get_slice(anyByteBuffer, anyColumnParent, anySlicePredicate, anyConsistencyLevel)).thenReturn(Future.value[ColumnList](columns))

      cf.getColumn("key", "cats")() must equal(Some(CounterColumn("cats", 2L)))
    }
  }

  describe("getting a row") {
    val (client, cf) = setupCounters

    it("performs a get_slice with a maxed-out count") {
      cf.getRow("key")

      val cp = new thrift.ColumnParent("cf")

      val range = new thrift.SliceRange(b(""), b(""), false, Int.MaxValue)
      val pred = new thrift.SlicePredicate()
      pred.setSlice_range(range)

      verify(client).get_slice(b("key"), cp, pred, thrift.ConsistencyLevel.QUORUM)
    }

    it("returns a map of column names to columns") {
      val columns = Seq(cc("cats", 2L),
        cc("dogs", 4L))

      when(client.get_slice(anyByteBuffer, anyColumnParent, anySlicePredicate, anyConsistencyLevel)).thenReturn(Future.value[ColumnList](columns))

      cf.getRow("key")() must equal(asJavaMap(Map(
        "cats" -> CounterColumn("cats", 2L),
        "dogs" -> CounterColumn("dogs", 4L)
      )))
    }
  }

  describe("getting a set of columns for a key") {
    val (client, cf) = setupCounters

    it("performs a get_counter_slice with a set of column names") {
      cf.getColumns("key", Set("name", "age"))

      val cp = new thrift.ColumnParent("cf")

      val pred = ArgumentCaptor.forClass(classOf[thrift.SlicePredicate])

      verify(client).get_slice(matchEq(b("key")), matchEq(cp), pred.capture, matchEq(thrift.ConsistencyLevel.QUORUM))

      pred.getValue.getColumn_names.map { Utf8Codec.decode(_) } must equal(List("name", "age"))
    }

    it("returns a map of column names to columns") {
      val columns = Seq(cc("cats", 2L),
        cc("dogs", 3L))

      when(client.get_slice(anyByteBuffer, anyColumnParent, anySlicePredicate, anyConsistencyLevel)).thenReturn(Future.value[ColumnList](columns))

      cf.getColumns("key", Set("cats", "dogs"))() must equal(asJavaMap(Map(
        "cats" -> CounterColumn("cats", 2L),
        "dogs" -> CounterColumn("dogs", 3L)
      )))
    }
  }

  describe("getting a column for a set of keys") {
    val (client, cf) = setupCounters

    it("performs a multiget_counter_slice with a column name") {
      cf.consistency(ReadConsistency.One).multigetColumn(Set("key1", "key2"), "name")

      val keys = List("key1", "key2").map { Utf8Codec.encode(_) }
      val cp = new thrift.ColumnParent("cf")
      val pred = ArgumentCaptor.forClass(classOf[thrift.SlicePredicate])

      verify(client).multiget_slice(matchEq(keys), matchEq(cp), pred.capture, matchEq(thrift.ConsistencyLevel.ONE))

      pred.getValue.getColumn_names.map { Utf8Codec.decode(_) } must equal(List("name"))
    }

    it("returns a map of keys to a map of column names to columns") {
      val results = Map(
        b("us") -> asJavaList(Seq(cc("cats", 2L))),
        b("jp") -> asJavaList(Seq(cc("cats", 4L)))
      )

      when(client.multiget_slice(anyListOf(classOf[ByteBuffer]), anyColumnParent, anySlicePredicate, anyConsistencyLevel)).thenReturn(Future.value[KeyColumnMap](results))

      cf.multigetColumn(Set("us", "jp"), "cats")() must equal(asJavaMap(Map(
        "us" -> CounterColumn("cats", 2L),
        "jp" -> CounterColumn("cats", 4L)
      )))
    }

    it("does not explode when the column doesn't exist for a key") {
      val results = Map(
        b("us") -> asJavaList(Seq(cc("cats", 2L))),
        b("jp") -> (asJavaList(Seq()): ColumnList)
      )

      when(client.multiget_slice(anyListOf(classOf[ByteBuffer]), anyColumnParent, anySlicePredicate, anyConsistencyLevel)).thenReturn(Future.value[KeyColumnMap](results))

      cf.multigetColumn(Set("us", "jp"), "cats")() must equal(asJavaMap(Map(
        "us" -> CounterColumn("cats", 2L)
      )))
    }
  }

  describe("getting a set of columns for a set of keys") {
    val (client, cf) = setupCounters

    it("performs a multiget_counter_slice with a set of column names") {
      cf.consistency(ReadConsistency.One).multigetColumns(Set("us", "jp"), Set("cats", "dogs"))

      val keys = List("us", "jp").map { b(_) }
      val cp = new thrift.ColumnParent("cf")
      val pred = ArgumentCaptor.forClass(classOf[thrift.SlicePredicate])

      verify(client).multiget_slice(matchEq(keys), matchEq(cp), pred.capture, matchEq(thrift.ConsistencyLevel.ONE))

      pred.getValue.getColumn_names.map { Utf8Codec.decode(_) } must equal(List("cats", "dogs"))
    }

    it("returns a map of keys to a map of column names to columns") {
      val results = Map(
        b("us") -> asJavaList(Seq(cc("cats", 2L),
          cc("dogs", 9L))),
        b("jp") -> asJavaList(Seq(cc("cats", 4L),
          cc("dogs", 1L)))
      )

      when(client.multiget_slice(anyListOf(classOf[ByteBuffer]), anyColumnParent, anySlicePredicate, anyConsistencyLevel)).thenReturn(Future.value[KeyColumnMap](results))

      cf.multigetColumns(Set("us", "jp"), Set("cats", "dogs"))() must equal(asJavaMap(Map(
        "us" -> asJavaMap(Map(
          "cats" -> CounterColumn("cats", 2L),
          "dogs" -> CounterColumn("dogs", 9L)
        )),
        "jp" -> asJavaMap(Map(
          "cats" -> CounterColumn("cats", 4L),
          "dogs" -> CounterColumn("dogs", 1L)
        ))
      )))
    }
  }

  describe("adding a column") {
    val (client, cf) = setupCounters

    it("performs an add") {
      cf.add("key", CounterColumn("cats", 55))

      val cp = ArgumentCaptor.forClass(classOf[thrift.ColumnParent])
      val col = cc("cats", 55).counter_column

      verify(client).add(matchEq(b("key")), cp.capture, matchEq(col), matchEq(thrift.ConsistencyLevel.ONE))

      cp.getValue.getColumn_family must equal("cf")
    }
  }

  describe("performing a batch mutation") {
    val (client, cf) = setupCounters

    it("performs a batch_mutate") {
      cf.batch()
        .insert("key", CounterColumn("cats", 201))
        .execute()

      val map = ArgumentCaptor.forClass(classOf[java.util.Map[ByteBuffer, java.util.Map[String, java.util.List[thrift.Mutation]]]])

      verify(client).batch_mutate(map.capture, matchEq(thrift.ConsistencyLevel.ONE))

      val mutations = map.getValue
      val mutation = mutations.get(b("key")).get("cf").get(0)
      val col = mutation.getColumn_or_supercolumn.getCounter_column
      Utf8Codec.decode(col.name) must equal("cats")
      col.value must equal(201)
    }
  }
}
